package com.apex.model;

import java.util.List;
import java.util.Map;

/**
 * A rentable vehicle of the fleet. Field names mirror the frontend JSON
 * contract 1:1 so Gson can bind both directions without adapters.
 */
public class Car {

    private int id;
    private String name;
    private String brand;
    private String category;
    private String image;
    private Integer year;
    private Integer rate;
    private Integer deposit;
    private Integer seats;
    private String engine;
    private String power;
    private String fuel;
    private String km;
    private String color;
    private String plate;
    private String condition;
    private String status;
    private String origin;
    private String marketNote;
    private String customImage;
    private String ownerId;
    private Double ownerShare;
    private Double companyShare;
    private List<String> features;
    private Map<String, String> customImages;

    public Car() { }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getRate() { return rate; }
    public void setRate(Integer rate) { this.rate = rate; }
    public Integer getDeposit() { return deposit; }
    public void setDeposit(Integer deposit) { this.deposit = deposit; }
    public Integer getSeats() { return seats; }
    public void setSeats(Integer seats) { this.seats = seats; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public String getPower() { return power; }
    public void setPower(String power) { this.power = power; }
    public String getFuel() { return fuel; }
    public void setFuel(String fuel) { this.fuel = fuel; }
    public String getKm() { return km; }
    public void setKm(String km) { this.km = km; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getMarketNote() { return marketNote; }
    public void setMarketNote(String marketNote) { this.marketNote = marketNote; }
    public String getCustomImage() { return customImage; }
    public void setCustomImage(String customImage) { this.customImage = customImage; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public Double getOwnerShare() { return ownerShare; }
    public void setOwnerShare(Double ownerShare) { this.ownerShare = ownerShare; }
    public Double getCompanyShare() { return companyShare; }
    public void setCompanyShare(Double companyShare) { this.companyShare = companyShare; }
    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }
    public Map<String, String> getCustomImages() { return customImages; }
    public void setCustomImages(Map<String, String> customImages) { this.customImages = customImages; }

    /** Server-side validation used by the admin CRUD endpoints. */
    public void validate() {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Car name is required");
        if (brand == null || brand.isBlank()) throw new IllegalArgumentException("Car brand is required");
        if (rate == null || rate <= 0) throw new IllegalArgumentException("Daily rate must be > 0");
    }
}
