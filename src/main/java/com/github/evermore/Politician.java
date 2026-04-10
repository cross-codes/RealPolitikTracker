package com.github.evermore;

public class Politician {
    public int ID;
    public String name;
    public boolean isIndian;
    public boolean isFemale;
    public int volatilityIndex;
    public int spectrum;
    public int total;
    public int basePrice;

    public Politician(int ID, String name, boolean isIndian, boolean isFemale, int volatilityIndex, int spectrum, int total, int basePrice) {
        this.ID = ID;
        this.name = name;
        this.isIndian = isIndian;
        this.isFemale = isFemale;
        this.volatilityIndex = volatilityIndex;
        this.spectrum = spectrum;
        this.total = total;
        this.basePrice = basePrice;
    }

    public Politician(String line) {
        String[] params = line.split(",");
        if (params.length != 10) {
            System.err.println("Incorrect CSV format received, expected 10 arguments");
        }

        // Sl.No, Leaders, Nationality, Gender, Mass appeal, Political Tact, Volatility Index, Spectrum, Total, Base price

        this.ID = Integer.parseInt(params[0]);
        this.name = params[1];
        this.isIndian = params[2].equals("I");
        this.isFemale = params[3].equals("F");
        this.volatilityIndex = Integer.parseInt(params[6]);
        this.spectrum = Integer.parseInt(params[7]);
        this.total = Integer.parseInt(params[8]);
        this.basePrice = Integer.parseInt(params[9]);
    }

    @Override
    public String toString() {
        return "ID: " + String.valueOf(this.ID) + " Name: " + this.name + " isIndian: " + String.valueOf(this.isIndian) + " isFemale: " + String.valueOf(this.isFemale) + " volatility: " + String.valueOf(this.volatilityIndex) + " spectrum: " + String.valueOf(this.spectrum) + "  total: " + String.valueOf(total) + " base price: " + String.valueOf(this.basePrice);
    }
}
