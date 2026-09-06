/* RomRaider2 ECU Studio - GPL 2.0 or later.
 * STI wordmark geometry: Subaru Tecnica International, https://www.sti.jp/en/.
 * The trademark belongs to its owner; this community theme is not endorsed by STI.
 */
package com.romraider.portable.gauge;

final class GaugeArtwork {
    private GaugeArtwork() { }
    private static final double[] STI = {
        0, 47.1, 28,
        1, 50.6, 28,
        1, 59.6, 11.7,
        1, 42.6, 11.7,
        1, 42.6, 28,
        1, 45.7, 28,
        1, 45.7, 14.9,
        1, 54.4, 14.9,
        1, 47.1, 28,
        3,
        0, 62.7, 0,
        1, 59.6, 5.5,
        1, 11.1, 5.5,
        2, 6.9, 5.5, 3.5, 9.1, 3.5, 13.3,
        2, 3.5, 17.5, 4.3, 17.4, 5.7, 18.8,
        2, 7.1, 20.3, 9, 21.1, 11.1, 21.1,
        1, 23.6, 21.1,
        1, 23.6, 18,
        1, 11.1, 18,
        2, 9.9, 18, 8.7, 17.5, 7.9, 16.6,
        2, 6.1, 14.8, 6.1, 11.8, 7.9, 10,
        2, 8.7, 9.1, 9.9, 8.6, 11.1, 8.6,
        1, 61.4, 8.6,
        1, 64.5, 3.1,
        1, 68.8, 3.1,
        1, 65.7, 8.6,
        1, 69.2, 8.6,
        1, 74, 0,
        1, 62.7, 0,
        3,
        0, 11.1, 14.9,
        1, 23.9, 14.9,
        2, 25.2, 14.9, 26.4, 15.4, 27.3, 16.4,
        2, 28.2, 17.4, 28.7, 18.6, 28.7, 20,
        2, 28.7, 22.7, 26.6, 24.8, 24, 24.9,
        1, 1.5, 24.9,
        1, 0, 28,
        1, 23.9, 28,
        2, 26, 28.1, 28, 27.2, 29.5, 25.7,
        2, 32.3, 22.7, 32.6, 18.1, 30.1, 14.9,
        1, 35.2, 14.9,
        1, 35.2, 28,
        1, 38.3, 28,
        1, 38.3, 11.7,
        1, 11.1, 11.7,
        1, 11.1, 14.9,
        3,
        0, 54.9, 28,
        1, 58.4, 28,
        1, 67.4, 11.7,
        1, 64, 11.7,
        1, 54.9, 28,
        3,
    };
    static void sti(GaugeFaceRenderer.Surface surface, double x, double y, double width, int color) {
        double[] path = STI.clone();
        double scale = width / 74;
        for (int i = 0; i < path.length;) {
            int op = (int) path[i++];
            int count = op == 2 ? 6 : op == 3 ? 0 : 2;
            for (int coordinate = 0; coordinate < count; coordinate++) {
                path[i] = path[i] * scale + (coordinate % 2 == 0 ? x : y);
                i++;
            }
        }
        surface.path(path, color);
    }
}
