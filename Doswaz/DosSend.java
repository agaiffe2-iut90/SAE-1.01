import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.awt.Graphics;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.util.Arrays;











public class DosSend {
    final int FECH = 44100; // fréquence d'échantillonnage
    final int FP = 1000;    // fréquence de la porteuses
    final int BAUDS = 100;  // débit en symboles par seconde
    final int FMT = 16 ;    // format des données
    final int MAX_AMP = (1<<(FMT-1))-1; // amplitude max en entier
    final int CHANNELS = 1; // nombre de voies audio (1 = mono)
    final int[] START_SEQ = {1,0,1,0,1,0,1,0}; // séquence de synchro au début
    final Scanner input = new Scanner(System.in); // pour lire le fichier texte

    long taille;                // nombre d'octets de données à transmettre
    double duree ;              // durée de l'audio
    double[] dataMod;           // données modulées
    char[] dataChar;            // données en char
    FileOutputStream outStream; // flux de sortie pour le fichier .wav


    /**
     * Constructor
     * @param path  the path of the wav file to create
     */
    public DosSend(String path){
        File file = new File(path);
        try{
            outStream = new FileOutputStream(file);
        } catch (Exception e) {
            System.out.println("Erreur de création du fichier");
        }
    }
     /**
     * Close the output stream.
     */
    public void close() {
        try {
            if (outStream != null) {
                outStream.close();
                System.out.println("Fichier fermé avec succès.");
            }
        } catch (Exception e) {
            System.out.println("Erreur lors de la fermeture du fichier : " + e.getMessage());
        }
    }


    /**
     * Write a raw 4-byte integer in little endian
     * @param octets    the integer to write
     * @param destStream  the stream to write in
     */
    public void writeLittleEndian(int octets, int taille, FileOutputStream destStream){
        char poidsFaible;
        while(taille > 0){
            poidsFaible = (char) (octets & 0xFF);
            try {
                destStream.write(poidsFaible);
            } catch (Exception e) {
                System.out.println("Erreur d'écriture");
            }
            octets = octets >> 8;
            taille--;
        }
    }

    /**
     * Create and write the header of a wav file
     *
     */
    public void writeWavHeader(){
        taille = (long)(FECH * duree);
        long nbBytes = taille * CHANNELS * FMT / 8;

        try  {
            outStream.write(new byte[]{'R', 'I', 'F', 'F'});
            writeLittleEndian((int) (36 + nbBytes), 4, outStream);
            outStream.write(new byte[]{'W', 'A', 'V', 'E'});
            outStream.write(new byte[]{'f', 'm', 't', ' '});
            writeLittleEndian(16, 4, outStream); // taille du chunk 'fmt '
            writeLittleEndian(1, 2, outStream);  // format de l'audio (PCM)
            writeLittleEndian(CHANNELS, 2, outStream);
            writeLittleEndian(FECH, 4, outStream);
            writeLittleEndian(FECH * FMT * CHANNELS / 8, 4, outStream); // byte rate
            writeLittleEndian(FMT * CHANNELS / 8, 2, outStream); // block align
            writeLittleEndian(FMT, 2, outStream); // bits par échantillon
            outStream.write(new byte[]{'d', 'a', 't', 'a'});
            writeLittleEndian((int) nbBytes, 4, outStream);
        } catch(Exception e){
            System.out.printf(e.toString());
        }
    }


    /**
     * Write the data in the wav file
     * after normalizing its amplitude to the maximum value of the format (8 bits signed)
     */
    public void writeNormalizeWavData(){
        try {
            for (double datum : dataMod) {
                short normalized = (short) (datum * MAX_AMP);
                writeLittleEndian(normalized, FMT / 8, outStream);
            }
        } catch (Exception e) {
            System.out.println("Erreur d'écriture");
        }
    }

    /**
     * Read the text data to encode and store them into dataChar
     * @return the number of characters read
     */
    public int readTextData(){
        System.out.println("Veuillez entrer le texte à envoyer :");
        String text = input.nextLine();
        dataChar = text.toCharArray();
        return dataChar.length;
    }

    /**
     * convert a char array to a bit array
     * @param chars
     * @return byte array containing only 0 & 1
     */
    public byte[] charToBits(char[] chars){
        byte[] bits = new byte[chars.length * 8];
        for (int i = 0; i < chars.length; i++) {
            for (int j = 0; j < 8; j++) {
                bits[i * 8 + j] = (byte) ((chars[i] >> (7 - j)) & 1);
            }
        }
        return bits;
        
    }

    /**
     * Modulate the data to send and apply the symbol throughput via BAUDS and FECH.
     * @param bits the data to modulate
     */
    public void modulateData(byte[] bits, double amplification) {
        int samplesPerSymbol = FECH / BAUDS;
        int totalSamples = bits.length * samplesPerSymbol;
        double[] modulatedData = new double[totalSamples];
    
        for (int i = 0; i < bits.length; i++) {
            double amplitude = bits[i] == 1 ? amplification : -amplification;
            double phaseShift = 2 * Math.PI * FP / FECH;
    
            for (int j = 0; j < samplesPerSymbol; j++) {
                int index = i * samplesPerSymbol + j;
                modulatedData[index] = amplitude * Math.sin(phaseShift * j);
            }
        }
    
        dataMod = modulatedData;
    }



    /**
     * Affiche un signal sous forme de tableau d'entiers.
     *
     * @param sig    le signal à afficher (tableau d'entiers)
     * @param start  le premier échantillon à afficher
     * @param stop   le dernier échantillon à afficher
     * @param mode   "line" ou "point"
     * @param title  le titre de la fenêtre
     */
    public static void displaySig(int[] sig, int start, int stop, String mode, String title) {
        StdDraw.clear();
        StdDraw.setCanvasSize(800, 400);

        int panelWidth = 800;
        int panelHeight = 400;
        int numSamples = stop - start;
        double timeScale = (double) panelWidth / numSamples;

        for (int i = start; i < stop - 1; i++) {
            double t1 = (i - start) * timeScale;
            double t2 = (i + 1 - start) * timeScale;
            int y1 = sig[i];
            int y2 = sig[i + 1];

            if (mode.equals("line")) {
                StdDraw.line(t1, y1, t2, y2);
            } else if (mode.equals("point")) {
                StdDraw.point(t1, y1);
            }
        }

        StdDraw.show();
    }

    /**
     * Affiche un signal sous forme de tableau de réels.
     *
     * @param sig    le signal à afficher (tableau de réels)
     * @param start  le premier échantillon à afficher
     * @param stop   le dernier échantillon à afficher
     * @param mode   "line" ou "point"
     * @param title  le titre de la fenêtre
     */
    public static void displaySig(double[] sig, int start, int stop, String mode, String title) {
        StdDraw.clear();
        StdDraw.setCanvasSize(800, 400);

        int panelWidth = 800;
        int panelHeight = 400;
        int numSamples = stop - start;
        double timeScale = (double) panelWidth / numSamples;

        for (int i = start; i < stop - 1; i++) {
            double t1 = (i - start) * timeScale;
            double t2 = (i + 1 - start) * timeScale;
            double y1 = sig[i];
            double y2 = sig[i + 1];

            if (mode.equals("line")) {
                StdDraw.line(t1, y1, t2, y2);
            } else if (mode.equals("point")) {
                StdDraw.point(t1, y1);
            }
        }

        StdDraw.show();
    }

    /**
     * Affiche plusieurs signaux sous forme de tableaux de réels, les signaux étant regroupés dans une liste de tableaux.
     *
     * @param listOfSigs la liste des signaux à afficher (liste de tableaux de réels)
     * @param start      le premier échantillon à afficher
     * @param stop       le dernier échantillon à afficher
     * @param mode       "line" ou "point"
     * @param title      le titre de la fenêtre
     */
    public static void displaySig(List<double[]> listOfSigs, int start, int stop, String mode, String title) {
        StdDraw.clear();
        StdDraw.setCanvasSize(800, 400);

        int panelWidth = 800;
        int panelHeight = 400;
        int numSamples = stop - start;
        int xScale = panelWidth / numSamples;

        for (double[] sig : listOfSigs) {
            for (int i = start; i < stop - 1; i++) {
                int x1 = (i - start) * xScale;
                int x2 = (i + 1 - start) * xScale;
                int y1 = (int) (sig[i] * panelHeight / 2 + panelHeight / 2);
                int y2 = (int) (sig[i + 1] * panelHeight / 2 + panelHeight / 2);

                if (mode.equals("line")) {
                    StdDraw.line(x1, y1, x2, y2);
                } else if (mode.equals("point")) {
                    StdDraw.point(x1, y1);
                }
            }
        }

        StdDraw.show();
    
    }

      public static void main(String[] args) {
        StdDraw.enableDoubleBuffering();
        // créé un objet DosSend
        DosSend dosSend = new DosSend("DosOok_message.wav");

        try {
            // lit le texte à envoyer depuis l'entrée standard
            // et calcule la durée de l'audio correspondant
            dosSend.duree = (double) (dosSend.readTextData() + dosSend.START_SEQ.length / 8) * 8.0 / dosSend.BAUDS;

            // génère le signal modulé après avoir converti les données en bits
            dosSend.modulateData(dosSend.charToBits(dosSend.dataChar), 0.10);

            // écrit l'entête du fichier wav
            dosSend.writeWavHeader();
            // écrit les données audio dans le fichier wav
            dosSend.writeNormalizeWavData();

            // affiche les caractéristiques du signal dans la console
            System.out.println("Message : "+String.valueOf(dosSend.dataChar));
            System.out.println("\tNombre de symboles : " + dosSend.dataChar.length);
            System.out.println("\tNombre d'échantillons : " + dosSend.dataMod.length);
            System.out.println("\tDurée : " + dosSend.duree + " s");
            System.out.println();

            // exemple d'affichage du signal modulé dans une fenêtre graphique
            displaySig(dosSend.dataMod, 1000, 3000, "line", "Signal modulé");
        } finally {
            // Fermer le flux dans le bloc finally pour garantir la fermeture, même en cas d'exception.
            dosSend.close();
            
        }
       
        Scanner scanner = new Scanner(System.in);
        System.out.println("Entrez une chaîne de caractères : ");
        String inputString = scanner.nextLine();
        char[] charArray = inputString.toCharArray();
        byte[] bitArray = dosSend.charToBits(charArray);
        System.out.println("Tableau de bits : " + Arrays.toString(bitArray));
        scanner.close();
        
    
    }
}
