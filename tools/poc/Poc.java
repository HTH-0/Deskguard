import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jev PoC runner. 사용법:
 * java Poc.java data.tsv questions.tsv out.csv reps ids|ALL variantLabel delayMs
 * data.tsv: id, group, label, tag, text (탭 구분)
 * questions.tsv: id, instructions, true, false (탭 구분)
 */
public class Poc {

    static final String MODEL = "jev-1.14.0";
    static final String URL = "https://api.typesafe.ai/v1/systemone";

    static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.toString();
    }

    static List<String[]> readTsv(String path, int cols) throws IOException {
        List<String[]> out = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            out.add(line.split("\t", cols));
        }
        return out;
    }

    public static void main(String[] a) throws Exception {
        String key = System.getenv("TYPESAFE_API_KEY");
        if (key == null || key.isBlank()) {
            System.out.println("NO_KEY");
            System.exit(2);
        }
        List<String[]> data = readTsv(a[0], 5);
        List<String[]> qs = readTsv(a[1], 4);
        String outPath = a[2];
        int reps = Integer.parseInt(a[3]);
        Set<String> only = new HashSet<>();
        if (!a[4].equals("ALL")) only.addAll(Arrays.asList(a[4].split(",")));
        String variant = a[5];
        long delay = Long.parseLong(a[6]);

        StringBuilder qj = new StringBuilder("{");
        List<String> qids = new ArrayList<>();
        for (int i = 0; i < qs.size(); i++) {
            String[] q = qs.get(i);
            qids.add(q[0]);
            if (i > 0) qj.append(",");
            qj.append("\"").append(q[0]).append("\":{\"type\":\"noul\",\"instructions\":\"")
              .append(esc(q[1])).append("\",\"criteria\":{\"true\":\"").append(esc(q[2]))
              .append("\",\"false\":\"").append(esc(q[3])).append("\"}}");
        }
        qj.append("}");
        String questionsJson = qj.toString();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        List<String[]> items = new ArrayList<>();
        for (String[] d : data) if (only.isEmpty() || only.contains(d[0])) items.add(d);

        StringBuilder csv = new StringBuilder();
        csv.append("id,group,label,tag,variant,rep,status,latency_ms,retries,model,input_tokens");
        for (String id : qids) csv.append(",").append(id);
        csv.append(",error\n");

        int total = items.size() * reps;
        int n = 0;
        for (int rep = 1; rep <= reps; rep++) {
            for (String[] it : items) {
                n++;
                String body = "{\"model\":\"" + MODEL + "\",\"state\":\"" + esc(it[4]) + "\",\"questions\":" + questionsJson + "}";
                int status = -1;
                int retries = 0;
                long latency = 0;
                String resp = "";
                String err = "";
                for (int attempt = 0; attempt <= 3; attempt++) {
                    long t0 = System.nanoTime();
                    try {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                            .timeout(Duration.ofSeconds(30))
                            .header("Authorization", "Bearer " + key)
                            .header("Content-Type", "application/json; charset=utf-8")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
                        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                        latency = (System.nanoTime() - t0) / 1_000_000;
                        status = r.statusCode();
                        resp = r.body();
                        if (status == 429 || status == 529) {
                            retries++;
                            Thread.sleep(1000L << attempt);
                            continue;
                        }
                        break;
                    } catch (Exception e) {
                        latency = (System.nanoTime() - t0) / 1_000_000;
                        status = -1;
                        err = e.getClass().getSimpleName();
                        retries++;
                        Thread.sleep(1000L << attempt);
                    }
                }
                String model = "";
                String tokens = "";
                StringBuilder probs = new StringBuilder();
                if (status == 200) {
                    Matcher mm = Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"").matcher(resp);
                    if (mm.find()) model = mm.group(1);
                    Matcher tm = Pattern.compile("\"input_tokens\"\\s*:\\s*(\\d+)").matcher(resp);
                    if (tm.find()) tokens = tm.group(1);
                    for (String id : qids) {
                        Matcher pm = Pattern.compile("\"" + id + "\"\\s*:\\s*\\{[^}]*?\"noul\"\\s*:\\s*([-0-9.eE+]+)").matcher(resp);
                        probs.append(",").append(pm.find() ? pm.group(1) : "");
                    }
                } else {
                    for (int i = 0; i < qids.size(); i++) probs.append(",");
                    if (err.isEmpty()) err = resp.replaceAll("[,\\r\\n]", " ");
                    if (err.length() > 200) err = err.substring(0, 200);
                }
                csv.append(it[0]).append(",").append(it[1]).append(",").append(it[2]).append(",")
                   .append(it[3]).append(",").append(variant).append(",").append(rep).append(",")
                   .append(status).append(",").append(latency).append(",").append(retries).append(",")
                   .append(model).append(",").append(tokens).append(probs).append(",").append(err).append("\n");
                if (n % 25 == 0) {
                    Files.writeString(Paths.get(outPath), csv.toString(), StandardCharsets.UTF_8);
                    System.out.println("progress " + n + "/" + total);
                }
                if (delay > 0) Thread.sleep(delay);
            }
        }
        Files.writeString(Paths.get(outPath), csv.toString(), StandardCharsets.UTF_8);
        System.out.println("done " + n + "/" + total);
    }
}
