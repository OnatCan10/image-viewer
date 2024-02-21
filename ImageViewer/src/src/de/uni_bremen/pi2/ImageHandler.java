package de.uni_bremen.pi2;

import jdk.internal.jimage.ImageReader;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Instanzen dieser Klasse bieten ein Bild in verschiedenen Formaten an.
 * @author Onat Can Vardareri
 */
class ImageHandler {
    /**
     * Der Farbwert schwarzer Pixel (RGBA = (0, 0, 0, 255).
     */
    private static final int BLACK = 0xff000000;

    /**
     * Der Farbwert weißer Pixel (RGBA = (255, 255, 255, 255).
     */
    private static final int WHITE = 0xffffffff;

    /**
     * Das Orignialbild.
     */
    private final BufferedImage original;

    /**
     * Ein Grauwertbild. null, wenn es noch nicht berechnet wurde.
     */
    private BufferedImage grayscale;

    /**
     * Ein Schwarzweißbild. null, wenn es noch nicht berechnet wurde.
     */
    private BufferedImage blackAndWhite;

    /**
     * Ein segmentiertes Bild. null, wenn es noch nicht berechnet wurde.
     */
    private BufferedImage segmented;

    /**
     * Zufallszahlengenerator zum Erzeugen zufälliger Farben.
     */
    private final Random random = new Random(0);

    //kodiert zusammenhängende Bereiche
    class Run {
        //der Anfangswert des x-Bereiches
        int x_start;
        //der Endwert des x-Bereiches
        int x_end;
        //der y-Wert
        int y;
        //der Verweis auf den übergeordneten (Eltern-)Run (siehe Aufgabe 2.2)
        Run parentRun;

        /**
         * Der Konstruktor der Klasse Run
         * zu Beginn verweis jeder Run auf sich selbst als Eltern-Run
         *
         * @param x_start Startwert des x-Bereiches
         * @param x_end   Endwert des x-Bereiches
         * @param y       y-Wert
         */
        Run(int x_start, int x_end, int y) {
            this.x_start = x_start;
            this.x_end = x_end;
            this.y = y;
            this.parentRun = this;
        }

        /**
         * Überprüft, ob der Elternverweis des Runs gleich dem Run ist. Trifft dies zu, handelt es sich um eine Wurzel
         * und der Run wird zurückgegeben. Ist der Run nicht sein eigener ElternRun. wird die Methode getRoot()
         * rekursiv auf den Eltern-Run aufgerufen, um das gleiche Verfahren durchzuführen
         * Dabei wird der Pfad gleichzeitig komprimiert, nämlich durch die Halbierungsmethode
         *
         * @return der Run, der die Wurzel des Runs darstellt, auf den diese Methode aufgerufen wird
         */
        Run getRoot() {
            if (parentRun == this) {
                return parentRun;
            } else {
                //Halbierungsmethode
                this.parentRun = this.parentRun.parentRun;
                //rekursiver Aufruf auf Elternverweis
                return parentRun.getRoot();
            }
        }
    }

    /**
     * Konstruktor.
     *
     * @param image Das Bild, das in verschiedenen Formaten bereit gestellt wird.
     */
    ImageHandler(final BufferedImage image) {
        original = image;
    }

    /**
     * Liefert das Originalbild.
     *
     * @return Das Orignalbild.
     */
    BufferedImage getOriginal() {
        return original;
    }

    /**
     * Liefert das Bild in Grauwerten.
     * Das Grauwertbild wird bei Bedarf erst berechnet.
     *
     * @return Das Grauwertbild.
     */
    BufferedImage getGrayscale() {
        if (grayscale == null) {
            final BufferedImage image = getOriginal();
            grayscale = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            for (int y = 0; y < image.getHeight(); ++y) {
                for (int x = 0; x < image.getWidth(); ++x) {
                    grayscale.setRGB(x, y, image.getRGB(x, y));
                }
            }
        }
        return grayscale;
    }

    /**
     * Liefert das Bild in Schwarzweiß.
     * Das Schwarzweißbild wird bei Bedarf erst berechnet, indem für das Grauwertbild der sog. Otsu-Schwellwert
     * bestimmt wird. Alle Grautöne unterhalb des Schwellwerts werden schwarz, alle darüber weiß.
     *
     * @return Das Schwarzweißbild.
     */
    BufferedImage getBlackAndWhite() {
        if (blackAndWhite == null) {
            final BufferedImage image = getGrayscale();
            blackAndWhite = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            final int threshold = otsuThreshold(image);
            for (int y = 0; y < image.getHeight(); ++y) {
                for (int x = 0; x < image.getWidth(); ++x) {
                    blackAndWhite.setRGB(x, y, (image.getRGB(x, y) & 255) <= threshold ? 0 : -1);
                }
            }
        }
        return blackAndWhite;
    }

    /**
     * Liefert ein segmentiertes Bild, bei dem alle zusammenhängenden, weißen Regionen
     * des Schwarzweißbildes mit zufälligen Farben markiert sind. Das Bild wird erst bei
     * Bedarf berechnet.
     *
     * @return Das segmentierte Bild.
     */
    BufferedImage getSegmented() {
        //Das Bild wird erst bei Bedarf berechnet
        if (segmented == null) {
            //zunächst wird das Bild ins schwarz-weiß-Format überführt und in segmented gespeichert
            final BufferedImage image = getBlackAndWhite();
            segmented = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);

            //Eine Array-List aus Runs wird initialisiert
            ArrayList<Run> runs = new ArrayList<Run>();
            //Indizes, um sich die x-Bereiche des jeweiligen Runs zu merken
            int start = 0;
            int end = 0;
            //Das Bild wird von oben-links bis unten-rechts durchlaufen
            for (int y = 0; y < image.getHeight(); ++y) {
                for (int x = 0; x < image.getWidth(); ++x) {
                    //wenn ein Pixel weiß ist, wird dessen x-Position als Start-Punkt eines neuen Runs gespeichert
                    if (image.getRGB(x, y) == WHITE) {
                        start = x;
                        // x wird so lange hochgezählt, bis der Zähler das Ende der Zeile erreicht oder ein Pixel
                        //schwarz ist. Der x-Wert dieses Punktes wird als End-Position des aktuellen Runs gespeichert
                        while (x < segmented.getWidth() && image.getRGB(x, y) == WHITE) {
                            x++;
                        }
                        end = x;
                        //Ein neuer Run wird mit den entsprechenden Parametern initialisiert und der Array-List hinzugefügt
                        runs.add(new Run(start, end, y));
                    }
                }
            }
            //Zwei Indizes um die Regionen (Runs) zu durchlaufen
            int i = 0;
            int j = 0;
            //Ausführung des Codes, solange der vorlaufende Index noch nicht das Ende der Zusammenfassung der Runs erreicht hat
            while (i < runs.size()) {
                //Bedinung zum Vereinigen von zwei Regionen (Runs) (siehe Übungsblatt Aufgabe 2.4)
                if (((runs.get(j).x_start < runs.get(i).x_end) && (runs.get(i).x_start < runs.get(j).x_end) && (runs.get(j).y + 1 == runs.get(i).y))) {
                    //Vereinigung zweier Regionen
                    merge(runs.get(j), runs.get(i));
                }
                // Bedinung zum Hochzählen von j und gegensätzlich von i (siehe Aufgabe 2.5)
                if ((runs.get(j).y + 1 < runs.get(i).y) || (runs.get(j).y + 1 == runs.get(i).y && runs.get(j).x_end < runs.get(i).x_end)) {
                    j++;
                } else {
                    i++;
                }
            }
            //Initialisieren einer Variable, welche die Farbe speichert
            int color = WHITE;
            //zählt die ArrayList runs durch
            for (int z = 0; z < runs.size(); z++) {
                //Wenn Run eine Wurzel ist, also parentRun auf sich selbst zeigt, wird eine neue Farbe ermittelt
                if (runs.get(z) == runs.get(z).getRoot()) {
                    color = getRandomColor();
                }
                //Die Pixel innerhalb dieses Runs werden durchgezählt und mit der Farbe eingefärbt
                for (int a = runs.get(z).x_start; a < runs.get(z).x_end; a++) {
                    segmented.setRGB(a, runs.get(z).y, color);
                }
            }
        }
        //das gemalte Bild wird zurückgegeben
        return segmented;
    }

    /**
     * Vereinigt zwei Regionen zu einer, indem die Wurzeln der beiden Elemente vereinigt werden
     *
     * @param run1 Einer der zu vereinigenden Runs
     * @param run2 Einer der zu vereinigenden Runs
     */
    void merge(Run run1, Run run2) {
        //ermittelt die Wurzel der beiden Runs
        Run root1 = run1.getRoot();
        Run root2 = run2.getRoot();
        //überprüft, welcher Run der Elternverweis werden soll. (siehe Kriterien auf dem Übungsblatt Aufgabe 2.3)
        //Dabei soll der Run, der weiter oben, bzw. weiter links ist, in der Datenstruktur als erster angegeben sein
        if ((root1.y < root2.y) || (root1.y == root2.y && root1.x_start < root2.x_start)) {
            //Zuweisung des einen Runs als Eltern-Run
            run2.getRoot().parentRun = run1.getRoot();
        } else {
            //Zuweisung des einen Runs als Eltern-Run
            run1.getRoot().parentRun = run2.getRoot();
        }
    }

    /**
     * Bestimmung des Otsu-Schwellwerts.
     * Vgl. z.B. http://www.labbookpages.co.uk/software/imgProc/otsuThreshold.html .
     *
     * @param image Das Grauwertbild, zu dem der beste Schwellwert zwischen Schwarz
     *              und Weiß bestimmt wird.
     */
    private int otsuThreshold(final BufferedImage image) {
        final int[] histogram = new int[256];
        for (int y = 0; y < image.getHeight(); ++y) {
            for (int x = 0; x < image.getWidth(); ++x) {
                ++histogram[image.getRGB(x, y) & 255];
            }
        }

        int countLow = 0; // Anzahl Pixel bis zum Schwellwert
        int countHigh = image.getWidth() * image.getHeight(); // Anzahl über Schwellwert
        int sumLow = 0; // Gewichtete Summe der Pixel bis zum Schwellwert
        int sumHigh = 0; // Gewichtete Summe der Pixel über dem Schwellwert

        for (int i = 0; i < histogram.length; ++i) {
            sumHigh += histogram[i] * i;
        }

        int bestThreshold = 0;
        double bestVar = 0;
        for (int threshold = 0; threshold < histogram.length; ++threshold) {
            countLow += histogram[threshold];
            countHigh -= histogram[threshold];
            sumLow += histogram[threshold] * threshold;
            sumHigh -= histogram[threshold] * threshold;
            if (countLow > 0 && countHigh > 0) {
                double avgLow = (double) sumLow / countLow;
                double avgHigh = (double) sumHigh / countHigh;
                double diff = avgHigh - avgLow;
                double var = (double) countLow * countHigh * diff * diff;
                if (var > bestVar) {
                    bestVar = var;
                    bestThreshold = threshold;
                }
            }
        }

        return bestThreshold;
    }

    /**
     * Liefert eine zufällige Farbe, die mindestens 50% Sättigung und Helligkeit hat.
     *
     * @return Die Farbe, deren Rot-, Grün- und Blauanteile in einem int kodiert sind.
     */
    private int getRandomColor() {
        return Color.HSBtoRGB(random.nextFloat(),
                random.nextFloat() * 0.5f + 0.5f,
                random.nextFloat() * 0.5f + 0.5f);
    }

}
