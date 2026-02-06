import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class OuladRecommendation {

    // ==========================================
    // JOB 1: JOIN & FILTER (StudentVle + Vle)
    // ==========================================

    // Mapper pour VLE.csv
    public static class VleMapper extends Mapper<LongWritable, Text, Text, Text> {
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString();
            String[] parts = parseCsv(line);
            if (parts.length < 4 || parts[0].equals("id_site"))
                return;

            String id_site = parts[0];
            String code_module = parts[1];
            String code_presentation = parts[2];
            String activity_type = parts[3];

            if ("DDD".equals(code_module) && ("2013B".equals(code_presentation) || "2013J".equals(code_presentation))) {
                context.write(new Text(id_site), new Text("V|" + activity_type));
            }
        }
    }

    // Mapper pour StudentVle.csv
    public static class StudentVleMapper extends Mapper<LongWritable, Text, Text, Text> {
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString();
            String[] parts = parseCsv(line);
            if (parts.length < 6 || parts[0].equals("code_module"))
                return;

            String id_student = parts[2];
            String id_site = parts[3];
            String sum_click = parts[5];

            context.write(new Text(id_site), new Text("S|" + id_student + "|" + sum_click));
        }
    }

    public static class JoinReducer extends Reducer<Text, Text, Text, Text> {
        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            List<String> students = new ArrayList<>();
            String activityType = null;

            for (Text val : values) {
                String s = val.toString();
                if (s.startsWith("V|")) {
                    activityType = s.substring(2);
                } else if (s.startsWith("S|")) {
                    students.add(s.substring(2));
                }
            }

            if (activityType != null) {
                for (String studentData : students) {
                    String[] sd = studentData.split("\\|");
                    context.write(new Text(sd[0] + "," + key.toString() + "," + activityType), new Text(sd[1]));
                }
            }
        }
    }

    // ==========================================
    // JOB 2: AGGREGATION
    // ==========================================
    public static class AggregationMapper extends Mapper<LongWritable, Text, Text, Text> {
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString();
            String[] parts = line.split("\t");
            if (parts.length < 2)
                return;
            context.write(new Text(parts[0]), new Text(parts[1]));
        }
    }

    public static class AggregationReducer extends Reducer<Text, Text, Text, Text> {
        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            int totalClicks = 0;
            for (Text val : values) {
                try {
                    totalClicks += Integer.parseInt(val.toString());
                } catch (Exception e) {
                }
            }
            context.write(key, new Text(String.valueOf(totalClicks)));
        }
    }

    // ==========================================
    // JOB 3: UNIQUE FEATURES
    // ==========================================
    // ==========================================
    // JOB 3: UNIQUE FEATURES
    // ==========================================
    public static class FeatureMapper extends Mapper<LongWritable, Text, Text, Text> {
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] line = value.toString().split("\t");
            if (line.length < 2)
                return;
            String[] keys = line[0].split(",");
            if (keys.length < 3)
                return;
            // Requirement: id_site-activity_type
            String featureName = keys[1] + "-" + keys[2];
            context.write(new Text(featureName), new Text(line[1]));
        }
    }

    public static class FeatureReducer extends Reducer<Text, Text, Text, Text> {
        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            // We just need unique features, the value doesn't matter much for valid columns
            // list,
            // but we can sum it anyway or just verify existence.
            // The prompt says "find set of unique values", implies just list.
            // But we might use the sum later? Actually the scoring formula uses STUDENT'S
            // interaction by type.
            // So global sum is NOT needed for the formula "sum of interactions BY TYPE".
            // Confirmed schema: id_site-activity_type
            context.write(key, new Text("1")); // Value ignored
        }
    }

    // ==========================================
    // JOB 4: PIVOT TABLE
    // ==========================================
    public static class PivotMapper extends Mapper<LongWritable, Text, Text, Text> {
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] line = value.toString().split("\t");
            if (line.length < 2)
                return;
            String[] keys = line[0].split(",");
            if (keys.length < 3)
                return;
            String studentId = keys[0];
            String featureName = keys[1] + "-" + keys[2];
            String clicks = line[1];
            context.write(new Text(studentId), new Text(featureName + ":" + clicks));
        }
    }

    public static class PivotReducer extends Reducer<Text, Text, Text, Text> {
        private List<String> featureList = new ArrayList<>();

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            URI[] cacheFiles = context.getCacheFiles();
            if (cacheFiles != null && cacheFiles.length > 0) {
                try (BufferedReader reader = new BufferedReader(new FileReader("features.txt"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        featureList.add(line.split("\t")[0]);
                    }
                }
            }
        }

        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            Map<String, String> studentClicks = new HashMap<>();
            for (Text val : values) {
                String[] parts = val.toString().split(":");
                if (parts.length == 2)
                    studentClicks.put(parts[0], parts[1]);
            }
            StringBuilder vector = new StringBuilder();
            for (int i = 0; i < featureList.size(); i++) {
                if (i > 0)
                    vector.append(",");
                vector.append(studentClicks.getOrDefault(featureList.get(i), "0"));
            }
            context.write(key, new Text(vector.toString()));
        }
    }

    // ==========================================
    // JOB 5: SCORING
    // ==========================================
    public static class ScoringMapper extends Mapper<LongWritable, Text, Text, Text> {
        private List<String> featureList = new ArrayList<>();
        private List<String> featureTypes = new ArrayList<>();

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            URI[] cacheFiles = context.getCacheFiles();
            if (cacheFiles != null && cacheFiles.length > 0) {
                try (BufferedReader reader = new BufferedReader(new FileReader("features.txt"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Line format: id_site-activity_type \t dummy_val
                        String feature = line.split("\t")[0];
                        featureList.add(feature);

                        // Extract activity_type (everything after the first hyphen)
                        // Example: 123-video -> video
                        // Example: 456-resource-external -> resource-external (if any)
                        // Safe assumption: id_site is numeric, so split on first hyphen.
                        int hyphenIdx = feature.indexOf('-');
                        if (hyphenIdx != -1) {
                            featureTypes.add(feature.substring(hyphenIdx + 1));
                        } else {
                            featureTypes.add("unknown");
                        }
                    }
                }
            }
        }

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] line = value.toString().split("\t");
            if (line.length < 2)
                return;
            String studentId = line[0];
            String[] clicksStr = line[1].split(",");

            if (clicksStr.length != featureList.size())
                return;

            long studentTotal = 0;
            long[] clicks = new long[clicksStr.length];
            Map<String, Long> interactionsByType = new HashMap<>();

            // 1. Calculate totals and interactions by type for THIS student
            for (int i = 0; i < clicksStr.length; i++) {
                try {
                    clicks[i] = Long.parseLong(clicksStr[i]);
                } catch (NumberFormatException e) {
                    clicks[i] = 0;
                }

                if (clicks[i] > 0) {
                    studentTotal += clicks[i];
                    String type = featureTypes.get(i);
                    interactionsByType.put(type, interactionsByType.getOrDefault(type, 0L) + clicks[i]);
                }
            }

            if (studentTotal == 0)
                return;

            // 2. Calculate scores
            StringBuilder scores = new StringBuilder();
            for (int i = 0; i < clicks.length; i++) {
                double score = 0.0;

                // Rule a: If interaction exists, score is 0
                if (clicks[i] > 0) {
                    score = 0.0;
                } else {
                    // Rule b: sum of interactions by type * 100 / total interactions
                    String type = featureTypes.get(i);
                    long typeSum = interactionsByType.getOrDefault(type, 0L);
                    score = (double) (typeSum * 100) / studentTotal;
                }

                if (i > 0)
                    scores.append(",");
                scores.append(String.format("%.2f", score));
            }
            context.write(new Text(studentId), new Text(scores.toString()));
        }
    }

    private static String[] parseCsv(String line) {
        // Strip quotes as OULAD files wrap fields in quotes
        return line.replace("\"", "").split(",");
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        String outputBase = args[2];

        // JOB 1
        Job job1 = Job.getInstance(conf, "Join");
        job1.setJarByClass(OuladRecommendation.class);
        MultipleInputs.addInputPath(job1, new Path(args[0]), TextInputFormat.class, StudentVleMapper.class);
        MultipleInputs.addInputPath(job1, new Path(args[1]), TextInputFormat.class, VleMapper.class);
        job1.setReducerClass(JoinReducer.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(Text.class);
        FileOutputFormat.setOutputPath(job1, new Path(outputBase + "/job1_join"));
        if (!job1.waitForCompletion(true))
            System.exit(1);

        // JOB 2
        Job job2 = Job.getInstance(conf, "Agg");
        job2.setJarByClass(OuladRecommendation.class);
        job2.setMapperClass(AggregationMapper.class);
        job2.setReducerClass(AggregationReducer.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job2, new Path(outputBase + "/job1_join"));
        FileOutputFormat.setOutputPath(job2, new Path(outputBase + "/job2_agg"));
        if (!job2.waitForCompletion(true))
            System.exit(1);

        // JOB 3
        Job job3 = Job.getInstance(conf, "Feat");
        job3.setJarByClass(OuladRecommendation.class);
        job3.setMapperClass(FeatureMapper.class);
        job3.setReducerClass(FeatureReducer.class);
        job3.setOutputKeyClass(Text.class);
        job3.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job3, new Path(outputBase + "/job2_agg"));
        FileOutputFormat.setOutputPath(job3, new Path(outputBase + "/job3_features"));
        if (!job3.waitForCompletion(true))
            System.exit(1);

        URI featuresUri = new URI(outputBase + "/job3_features/part-r-00000#features.txt");

        // JOB 4
        Job job4 = Job.getInstance(conf, "Pivot");
        job4.addCacheFile(featuresUri);
        job4.setJarByClass(OuladRecommendation.class);
        job4.setMapperClass(PivotMapper.class);
        job4.setReducerClass(PivotReducer.class);
        job4.setOutputKeyClass(Text.class);
        job4.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job4, new Path(outputBase + "/job2_agg"));
        FileOutputFormat.setOutputPath(job4, new Path(outputBase + "/job4_pivot"));
        if (!job4.waitForCompletion(true))
            System.exit(1);

        // JOB 5
        Job job5 = Job.getInstance(conf, "Score");
        job5.addCacheFile(featuresUri);
        job5.setJarByClass(OuladRecommendation.class);
        job5.setMapperClass(ScoringMapper.class);
        job5.setNumReduceTasks(0);
        job5.setOutputKeyClass(Text.class);
        job5.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job5, new Path(outputBase + "/job4_pivot"));
        FileOutputFormat.setOutputPath(job5, new Path(outputBase + "/job5_scoring"));
        System.exit(job5.waitForCompletion(true) ? 0 : 1);
    }
}
