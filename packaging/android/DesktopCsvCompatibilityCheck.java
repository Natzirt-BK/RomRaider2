/* RomRaider2 ECU Studio - GPL 2.0 or later. */
import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.RomRaiderCsvLogParser;
import java.io.File;

/** Validate only the synthetic fixture exported by LoggerSetupInstrumentation. */
public final class DesktopCsvCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Synthetic Android CSV path required");
        LogDataset data = new RomRaiderCsvLogParser().parse(new File(args[0]));
        if (data.getChannelCount() != 3 || data.getRowCount() != 2
                || data.getTimeChannel().getIndex() != 0
                || data.getValue(0, 0) != 0 || data.getValue(1, 0) != 100
                || data.getValue(0, 1) != 750 || data.getValue(1, 1) != 800
                || data.getValue(0, 2) != 13.25 || data.getValue(1, 2) != 13.24
                || !data.getChannels().get(1).getUnits().equals("rpm")
                || !data.getChannels().get(2).getUnits().equals("V")) {
            throw new AssertionError("Android-to-desktop CSV compatibility failed");
        }
        System.out.println("PASS: Android-exported synthetic CSV opens in production desktop parser with exact values, time, and units.");
    }
}
