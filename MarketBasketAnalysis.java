import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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

public class MarketBasketAnalysis {

    // Mapper: LongWritable (Offset) -> Text (Transaction Line)
    // Output: Text (Pair "ItemA,ItemB"), Text (TransactionID)
    public static class PairsMapper extends Mapper<LongWritable, Text, Text, Text> {

        private Text pairKey = new Text();
        private Text transIdValue = new Text();

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString();
            // Supposons que le format est : TransID,Item1,Item2,Item3...
            // Ou TransID<TAB>Item1,Item2... ou autre delimiteur.
            // Base sur les standards courants, on split souvent par virgule ou espace.
            // Adaptons pour un format CSV classique : ID,Item1,Item2,...

            // Format attendu : ID => Item1, Item2, Item3...
            String[] parts = line.split("=>");
            if (parts.length < 2) {
                return; // Ligne mal formee ou vide
            }

            String transId = parts[0].trim();
            String itemsPart = parts[1];

            String[] tokens = itemsPart.split(",");
            List<String> items = new ArrayList<>();

            for (String token : tokens) {
                String item = token.trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }

            Collections.sort(items); // Tri initial (optionnel mais propre)

            // Generation de toutes les paires (Pattern 'Pairs')
            // Double boucle pour (n * (n-1)) / 2 paires
            for (int i = 0; i < items.size(); i++) {
                for (int j = i + 1; j < items.size(); j++) {
                    String itemA = items.get(i);
                    String itemB = items.get(j);

                    // Tri alphabetique CRUCIAL pour eviter les doublons (A,B) vs (B,A)
                    if (itemA.compareTo(itemB) < 0) {
                        pairKey.set("[" + itemA + ", " + itemB + "]");
                    } else {
                        pairKey.set("[" + itemB + ", " + itemA + "]");
                    }

                    transIdValue.set(transId);
                    context.write(pairKey, transIdValue);
                }
            }
        }
    }

    // Reducer: Text (Pair), Iterable<Text> (TransIDs)
    // Output: Text (Pair), Text (List of TransIDs)
    public static class PairsReducer extends Reducer<Text, Text, Text, Text> {

        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            StringBuilder transList = new StringBuilder();
            boolean first = true;

            for (Text val : values) {
                if (!first) {
                    transList.append(",");
                }
                transList.append(val.toString());
                first = false;
            }

            context.write(key, new Text(transList.toString()));
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Market Basket Analysis - Pairs");

        job.setJarByClass(MarketBasketAnalysis.class);
        job.setMapperClass(PairsMapper.class);
        job.setReducerClass(PairsReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        if (args.length < 2) {
            System.err.println("Usage: MarketBasketAnalysis <input path> <output path>");
            System.exit(-1);
        }

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
