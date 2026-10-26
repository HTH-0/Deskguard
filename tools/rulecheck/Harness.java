import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Harness {

    static List<String> read(String path) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                out.add(line.strip());
            }
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        List<String> rules = read(dir + "/rules.tsv");
        String[] setNames = {"normal", "boundary", "stress", "attack"};
        Map<String, List<String>> sets = new LinkedHashMap<>();
        Map<String, Set<Integer>> union = new LinkedHashMap<>();
        for (String n : setNames) {
            sets.put(n, read(dir + "/" + n + ".txt"));
            union.put(n, new TreeSet<>());
        }
        StringBuilder sb = new StringBuilder();
        int compileErrors = 0;
        int ruleCount = 0;
        for (String r : rules) {
            String[] f = r.split("\t", -1);
            String id = f[0];
            String target = f[1];
            String pat = f[2];
            String[] me = f[3].isEmpty() ? new String[0] : f[3].split("\\|\\|");
            String[] mn = f[4].isEmpty() ? new String[0] : f[4].split("\\|\\|");
            Pattern p;
            try {
                p = Pattern.compile(pat);
            } catch (PatternSyntaxException e) {
                sb.append("== ").append(id).append(" COMPILE_ERROR ").append(e.getDescription()).append("\n");
                compileErrors++;
                continue;
            }
            ruleCount++;
            sb.append("== ").append(id).append(" (").append(target).append(")\n");
            int meOk = 0;
            for (String s : me) {
                if (p.matcher(s).find()) {
                    meOk++;
                } else {
                    sb.append("    OWN_EXAMPLE_NOT_MATCHED: ").append(s).append("\n");
                }
            }
            sb.append("  own match_examples matched: ").append(meOk).append("/").append(me.length).append("\n");
            for (String s : mn) {
                if (p.matcher(s).find()) {
                    sb.append("    OWN_MUST_NOT_VIOLATED: ").append(s).append("\n");
                }
            }
            for (String n : setNames) {
                List<String> set = sets.get(n);
                for (int i = 0; i < set.size(); i++) {
                    if (p.matcher(set.get(i)).find()) {
                        union.get(n).add(i + 1);
                        sb.append("  HIT ").append(n).append("[").append(i + 1).append("] ").append(set.get(i)).append("\n");
                    }
                }
            }
        }
        sb.append("\n=== SUMMARY ===\n");
        sb.append("rules compiled: ").append(ruleCount).append(", compile errors: ").append(compileErrors).append("\n");
        for (String n : setNames) {
            sb.append(n).append(" union hits: ").append(union.get(n).size()).append("/").append(sets.get(n).size())
              .append(" ").append(union.get(n)).append("\n");
        }
        Files.writeString(Paths.get(dir + "/result.txt"), sb.toString(), StandardCharsets.UTF_8);
        System.out.println("done");
    }
}
