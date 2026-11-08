// Replays synthetic exports through the existing Android parser. See APPLE.md.
import com.villageclock.app.ExportParser;
import com.villageclock.app.Village;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;

public class AppleTimingParity {
    public static void main(String[] args) throws Exception {
        JSONArray vectors = new JSONArray(Files.readString(Path.of(args[0])));
        for (int i = 0; i < vectors.length(); i++) {
            JSONObject vector = vectors.getJSONObject(i);
            Village village = ExportParser.parse(vector.getString("exportJSON"), id -> "Item", 1793491200000L);
            JSONArray finishes = new JSONArray();
            for (Village.Upgrade job : village.upgrades) finishes.put((job.finishAt - village.exportedAt) / 1000);
            if (args.length > 1 && args[1].equals("--refresh")) {
                vector.put("expectedFinishSeconds", finishes);
            } else if (!vector.getJSONArray("expectedFinishSeconds").toString().equals(finishes.toString())) {
                throw new AssertionError(vector.getString("label") + ": " + finishes);
            }
        }
        if (args.length > 1 && args[1].equals("--refresh")) {
            Files.writeString(Path.of(args[0]), vectors.toString(2) + "\n");
        }
        System.out.println("Android parity: " + vectors.length() + " synthetic exports matched.");
    }
}
