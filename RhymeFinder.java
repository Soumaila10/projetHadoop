import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class RhymeFinder {

    // Mapper: LongWritable (Offset) -> Text (Line)
    // Output: Text (Suffix of 4 chars), Text (Word)
    public static class RhymeMapper extends Mapper<LongWritable, Text, Text, Text> {

        private Text suffixKey = new Text();
        private Text wordValue = new Text();

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String word = value.toString().trim();

            // Filtrage precoce (Early Filtering) : Longueur > 5
            if (word.length() > 5) {
                // Extraction des 4 derniers caracteres
                String suffix = word.substring(word.length() - 4);

                suffixKey.set(suffix);
                wordValue.set(word);

                context.write(suffixKey, wordValue);
            }
        }
    }

    // Reducer: Text (Suffix), Iterable<Text> (Words)
    // Output: Text (Suffix), Text (List of words)
    public static class RhymeReducer extends Reducer<Text, Text, Text, Text> {

        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            List<String> rhymeWords = new ArrayList<>();

            // Parcourir la liste des mots pour le suffixe donne
            for (Text val : values) {
                rhymeWords.add(val.toString());
            }

            // Condition de rime : La liste doit contenir plus d'un mot
            if (rhymeWords.size() > 1) {
                StringBuilder joinedWords = new StringBuilder();
                for (int i = 0; i < rhymeWords.size(); i++) {
                    joinedWords.append(rhymeWords.get(i));
                    if (i < rhymeWords.size() - 1) {
                        joinedWords.append(", ");
                    }
                }
                context.write(key, new Text(joinedWords.toString()));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Rhyme Finder");

        job.setJarByClass(RhymeFinder.class);

        job.setMapperClass(RhymeMapper.class);
        // Nous n'utilisons pas de Combiner ici car la concatenation de chaines dans le
        // combiner
        // complexifierait le type d'entree du Reducer (Text vs Iterable<Text> standard)
        // et le gain reseau est modere pour des listes de mots simples par rapport a
        // l'overhead de deserialisation.
        // L'architecture standard garantit la proprete et la scalabilite O(n).

        job.setReducerClass(RhymeReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        if (args.length < 2) {
            System.err.println("Usage: RhymeFinder <input path> <output path>");
            System.exit(-1);
        }

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
