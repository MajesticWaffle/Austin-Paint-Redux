package com.thiccindustries.APE2.io;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Array;

public class FileManager {
    private static final Color[] defaultPalette = { //TODO: add palettes
            new Color(0, 0, 0),
            new Color(63,63,63),
            new Color(127,127,127),
            new Color(255,255,255),
            new Color(0,0,255),
            new Color(0,0,127),
            new Color(0,255,0),
            new Color(0,127,0),
            new Color(255,0,0),
            new Color(127,0,0),
            new Color(0,255,255),
            new Color(0,127,127),
            new Color(255,255,0),
            new Color(127,127,0),
            new Color(255,0,255),
            new Color(127,0,127),
    };

    //Save the file
    public static void saveFileFromImage(String filePath, int[][][] pixelArray, Color[] palette){
        File fileToSave = new File(filePath);

        FileOutputStream fos = null;

        /**Doing this with a hex string is EXTREMELY dumb, but I dont care**/
        StringBuilder sb = new StringBuilder();

        /**Really austin?**/
        sb.append("41555354494E5041494E540056322E30"); // "AUSTIN.PAINT.v2.0"

        //Add palette information
        for(int i = 0; i < 16; i++){
            int r = palette[i].getRed();
            int g = palette[i].getGreen();
            int b = palette[i].getBlue();
            sb.append(String.format("%02X", r) + "" + String.format("%02X", g) + "" + String.format("%02X", b));
        }

        //Add legacy AP2 pixel array for APE and austin paint 2
        int[][][] _pixelArray = new int[32][32][7];

        for(int i = 0; i < 7; i++){
            for(int x = 0; x < 32; x++){
                for (int y = 0; y < 32; y++){
                    _pixelArray[x][y][i] = pixelArray[x][y][i];
                }
            }
        }

        int[][] flatPixelArray = flattenAPImage(_pixelArray);



        for(int y = 0; y < 32; y++) {
            for (int x = 0; x < 16; x++) {
                sb.append(Integer.toHexString(flatPixelArray[x * 2][y]) + Integer.toHexString(flatPixelArray[(x * 2) + 1][y]));
            }
        }

       // APR header
        sb.append("2E415041494E542E2E52454455582E2E"); // ".APAINT..REDUX.."

        int[] unusedColors = new int[7];
        boolean[] layerEmpty = new boolean[7];

        for(int i = 0; i < 7; i++){
            unusedColors[i] = findUnusedColor(_pixelArray, i);


            layerEmpty[i] = isLayerEmpty(_pixelArray, i);
            if(layerEmpty[i])
                sb.append("0");
            else
                sb.append("1");
        }

        sb.append("F");

        for(int i = 0; i < 7; i++){
            if(unusedColors[i] == -1){
                System.err.println("No transparency color for layer: " + i + "using 0. this WILL cause issues.");
                unusedColors[i] = 0;
            }

            if(layerEmpty[i]) //Skip unused layers to save on file space
                continue;

            for(int y = 0; y < 32; y++){
                for(int x = 0; x < 16; x++){
                    if(_pixelArray[(x * 2)][y][i] == -1)
                        _pixelArray[(x * 2)][y][i] = unusedColors[i];

                    if(_pixelArray[(x * 2) + 1][y][i] == -1)
                        _pixelArray[(x * 2) + 1][y][i] = unusedColors[i];

                    sb.append(Integer.toHexString(_pixelArray[x * 2][y][i]) + Integer.toHexString(_pixelArray[(x * 2) + 1][y][i]));
               }
            }
        }

        //Transparency Color Headers
        for(int i = 0; i < 7; i++){
            sb.append(Integer.toHexString(unusedColors[i]));
        }

        sb.append("F");
        /**Why does StringBuilder even need a function to do this, why can i not just cast it**/

        String hexDump = sb.toString();

        byte[] rawPixelData = new byte[hexDump.length() / 2];

        //Convert pixel array from one byte per pixel to 4 bits per pixel
        for(int i = 0; i < (rawPixelData.length * 2); i+=2){
            rawPixelData[(i / 2)] = (byte)((Character.digit(hexDump.charAt(i), 16) << 4)
                    + Character.digit(hexDump.charAt(i + 1), 16));
        }
        try {
            //Write file
            fileToSave.createNewFile();
            fos = new FileOutputStream(fileToSave);
            fos.write(rawPixelData);
            fos.close();
        }catch(IOException e){
            System.err.print("Unknown IO Error.");
        }
    }

    //Save BMP file
    public static void saveBMPFromImage(String filePath, int[][][] pixelArray, Color[] palette){
        int[][] flatPixelArray = flattenAPImage(pixelArray);
        StringBuilder sb = new StringBuilder();
        //Header Info
        sb.append("424D760200000000000076000000280000002000000020000000010004000000000000000000C40E0000C40E00001000000010000000");

        //Blue Green Red fuck bmp all my homies hate bmp
        for(int i = 0; i < 16; i++){
            int b = palette[i].getBlue();
            int g = palette[i].getGreen();
            int r = palette[i].getRed();

            sb.append(String.format("%02X", b) + "" + String.format("%02X", g) + "" + String.format("%02X", r) + "FF");
        }

        for(int y = 31; y >= 0; y--){
            for(int x = 0; x < 16; x++){
                sb.append(Integer.toHexString(flatPixelArray[x * 2][y]) + Integer.toHexString(flatPixelArray[(x * 2) + 1][y]));
            }
        }

        String hexDump = sb.toString();

        //Convert data
        byte[] byteData = new byte[hexDump.length() / 2];
        for(int i = 0; i < (byteData.length * 2); i+=2) {
            byteData[i / 2] = (byte)((Character.digit(hexDump.charAt(i), 16) << 4)
                                    + Character.digit(hexDump.charAt(i + 1), 16));
        }

        File file = new File(filePath);
        FileOutputStream fos;
        try {
            file.createNewFile();
            fos = new FileOutputStream(file);
            fos.write(byteData);
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private static int findUnusedColor(int[][][] pixelArray, int layer) {
        int unusedColor = 0;
        boolean colorUnused = false;
        for(int i = 0; i < 16 && !colorUnused; i++){
            colorUnused = true;
            unusedColor = i;
            for(int x = 0; x < 32 && colorUnused; x++){
                for(int y = 0; y < 32 && colorUnused; y++){
                    if(pixelArray[x][y][layer] == i) {
                        colorUnused = false;
                    }
                }
            }
        }

        //No unused colors
        if(colorUnused == false) {
            return -1;
        }
        else {
            return unusedColor;
        }
    }

    private static boolean isLayerEmpty(int[][][] pixelArray, int layer){
        boolean empty = true;
        for(int x = 0; x < 32 && empty; x++){
            for(int y = 0; y < 32 && empty; y++){
                if(pixelArray[x][y][layer] != -1)
                    empty = false;
            }
        }

        return empty;
    }

    //Load a saved file
    public static APFile loadPixelArrayFromFile(String filePath) {
        APFile fileLoaded = new APFile();
        fileLoaded.pixelArray = new int[32][32][7];

        for(int layer = 0; layer < 7; layer++) {
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    fileLoaded.pixelArray[x][y][layer] = -1;
                }
            }
        }

        //Create a local copy of the palette to prevent destructive changes
        /**yes, this causes a memory leak**/
        System.arraycopy(defaultPalette, 0, fileLoaded.palette, 0, 16);

        File file = new File(filePath);
        FileInputStream fis = null;
        byte[] rawPixelData = new byte[(int) file.length()];


        try {
            fis = new FileInputStream(file);
            fis.read(rawPixelData);
            fis.close();
        } catch (FileNotFoundException e) {
            System.err.print("File did not exist.");
            return fileLoaded;
        } catch (IOException e) {
            System.err.print("Unknown IO Error.");
        }
        //Get Color Palette Information
        for (int i = 0; i < (16 * 3); i += 3) {
            fileLoaded.palette[i / 3] = new Color(rawPixelData[i + 16] & 0xff, rawPixelData[i + 16 + 1] & 0xff, rawPixelData[i + 16 + 2] & 0xff);
        }

        if (rawPixelData.length == 576) {
            System.out.println("Loading A.P.E / AP2 file.");

            for(int i = 1; i < 7; i++){
                for (int y = 0; y < 32; y++) {
                    for (int x = 0; x < 32; x++){
                        fileLoaded.pixelArray[x][y][i] = -1;
                    }
                }
            }
            /**Converting from Byte -> Char -> Int is probably the dumbest thing ive ever done but it works**/
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 16; x++) {
                    Byte currentByte = rawPixelData[x + (y * 16) + 64];
                    char firstPixelHexChar = String.format("%02x", currentByte).charAt(0);
                    char secondPixelHexChar = String.format("%02x", currentByte).charAt(1);
                    fileLoaded.pixelArray[x * 2][y][0] = Character.digit(firstPixelHexChar, 16);

                    if(fileLoaded.pixelArray[x * 2][y][0] == 0)
                        fileLoaded.pixelArray[x * 2][y][0] = -1;
                    fileLoaded.pixelArray[x * 2 + 1][y][0] = Character.digit(secondPixelHexChar, 16);

                    if(fileLoaded.pixelArray[x * 2 + 1][y][0] == 0)
                        fileLoaded.pixelArray[x * 2 + 1][y][0] = -1;
                }
            }

        }
        if(rawPixelData.length > 576 && rawPixelData.length != 4180){

            boolean[] activeLayers = new boolean[7];
            int activeLayerTotal = 0;
            //Get active layers
            for(int i = 0; i < 4; i++){
                int toplayer = rawPixelData[592 + i] >> 4;
                int bottomlayer = rawPixelData[592 + i] & 0x0F;
                if(toplayer == 1){
                    activeLayers[(i * 2)] = true;
                    activeLayerTotal++;
                }
                if(bottomlayer == 1 && i != 4) {
                    activeLayers[(i * 2) + 1] = true;
                    activeLayerTotal++;
                }
            }


            int transColorOffset = (592 + 4 + (activeLayerTotal * 512));
            //Get transparency colors
            int[] transColors = new int[8];
            for(int i = 0; i < 4; i++){
                byte transparencyColor = rawPixelData[transColorOffset + i];
                char topHalf = String.format("%02x", transparencyColor ).charAt(0);
                transColors[i * 2] = Character.digit(topHalf, 16);
                char bottomHalf = String.format("%02x", transparencyColor).charAt(1);;
                transColors[i * 2 + 1] = Character.digit(bottomHalf, 16);
            }
            int layerPosOffset = 0;
            for(int i = 0; i < 7; i++){
                if(activeLayers[i] == false){
                    continue;
                }
                for (int y = 0; y < 32; y++) {
                    for (int x = 0; x < 16; x++) {
                        Byte currentByte = rawPixelData[x + (y * 16) + 592 + 4 + layerPosOffset];
                        char firstPixelHexChar = String.format("%02x", currentByte).charAt(0);
                        char secondPixelHexChar = String.format("%02x", currentByte).charAt(1);
                        fileLoaded.pixelArray[x * 2][y][i]     = Character.digit(firstPixelHexChar, 16);
                        fileLoaded.pixelArray[x * 2 + 1][y][i] = Character.digit(secondPixelHexChar, 16);

                        if(fileLoaded.pixelArray[x * 2][y][i] == transColors[i]){
                            fileLoaded.pixelArray[x * 2][y][i] = -1;
                        }

                        if(fileLoaded.pixelArray[x * 2 + 1][y][i] == transColors[i]){
                            fileLoaded.pixelArray[x * 2 + 1][y][i] = -1;
                        }
                    }
                }

                layerPosOffset+=512;
            }


        }
        if(rawPixelData.length == 4180){
            System.out.println("Loading uncompressed A.P.R file.");

            int transColorOffset = (592 + (7 * 512));
            //Get transparency colors
            int[] transColors = new int[8];
            for(int i = 0; i < 4; i++){
                byte transparencyColor = rawPixelData[transColorOffset + i];
                char topHalf = String.format("%02x", transparencyColor ).charAt(0);
                transColors[i * 2] = Character.digit(topHalf, 16);
                char bottomHalf = String.format("%02x", transparencyColor).charAt(1);;
                transColors[i * 2 + 1] = Character.digit(bottomHalf, 16);
            }

            int layerPosOffset = 0;
            for(int i = 0; i < 7; i++){
                for (int y = 0; y < 32; y++) {
                    for (int x = 0; x < 16; x++) {
                        Byte currentByte = rawPixelData[x + (y * 16) + 592 + layerPosOffset];
                        char firstPixelHexChar = String.format("%02x", currentByte).charAt(0);
                        char secondPixelHexChar = String.format("%02x", currentByte).charAt(1);
                        fileLoaded.pixelArray[x * 2][y][i]     = Character.digit(firstPixelHexChar, 16);
                        fileLoaded.pixelArray[x * 2 + 1][y][i] = Character.digit(secondPixelHexChar, 16);

                        if(fileLoaded.pixelArray[x * 2][y][i] == transColors[i]){
                            fileLoaded.pixelArray[x * 2][y][i] = -1;
                        }

                        if(fileLoaded.pixelArray[x * 2 + 1][y][i] == transColors[i]){
                            fileLoaded.pixelArray[x * 2 + 1][y][i] = -1;
                        }
                    }
                }
                layerPosOffset+=512;
            }


        }
        return fileLoaded;
    }

    /*This only supports compressed AP2 files*/
    public static int[][][] extractPixelArray(byte[] bytes) throws ArrayIndexOutOfBoundsException {

        int[][][] layeredPixelArray = new int[32][32][7];

        boolean[] activeLayers = new boolean[7];
        int activeLayerTotal = 0;
        //Get active layers
        for(int i = 0; i < 4; i++){
            int toplayer = bytes[592 + i] >> 4;
            int bottomlayer = bytes[592 + i] & 0x0F;
            if(toplayer == 1){
                activeLayers[(i * 2)] = true;
                activeLayerTotal++;
            }
            if(bottomlayer == 1 && i != 4) {
                activeLayers[(i * 2) + 1] = true;
                activeLayerTotal++;
            }
        }

        int transColorOffset = (592 + (7 * 512));
        //Get transparency colors
        int[] transColors = new int[8];
        for(int i = 0; i < 4; i++){
            byte transparencyColor = bytes[transColorOffset + i];
            char topHalf = String.format("%02x", transparencyColor ).charAt(0);
            transColors[i * 2] = Character.digit(topHalf, 16);
            char bottomHalf = String.format("%02x", transparencyColor).charAt(1);;
            transColors[i * 2 + 1] = Character.digit(bottomHalf, 16);
        }

        int layerPosOffset = 0;
        for(int i = 0; i < 7; i++){
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 16; x++) {
                    Byte currentByte = bytes[x + (y * 16) + 592 + 4 + layerPosOffset];
                    char firstPixelHexChar = String.format("%02x", currentByte).charAt(0);
                    char secondPixelHexChar = String.format("%02x", currentByte).charAt(1);
                    layeredPixelArray[x * 2][y][i]     = Character.digit(firstPixelHexChar, 16);
                    layeredPixelArray[x * 2 + 1][y][i] = Character.digit(secondPixelHexChar, 16);

                    if(layeredPixelArray[x * 2][y][i] == transColors[i]){
                        layeredPixelArray[x * 2][y][i] = -1;
                    }

                    if(layeredPixelArray[x * 2 + 1][y][i] == transColors[i]){
                        layeredPixelArray[x * 2 + 1][y][i] = -1;
                    }
                }
            }
            layerPosOffset+=512;
        }

        return layeredPixelArray;

    }

    //Flattens the Austin Paint Redux layered image into an array that can be read by APE / AP2
    public static int[][] flattenAPImage(int[][][] layeredArray){
        int[][] flattenedArray = new int[32][32];
        for(int i = 0; i < 7; i++){
            for(int y = 0; y < 32; y++){
                for(int x = 0; x < 32; x++){
                    if(layeredArray[x][y][i] == -1)
                        continue;

                    flattenedArray[x][y] = layeredArray[x][y][i];
                }
            }
        }
        return flattenedArray;
    }


    public static class APFile{
        public int[][][] pixelArray;
        public Color[] palette = new Color[16];
    }
}


