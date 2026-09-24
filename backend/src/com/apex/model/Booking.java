package com.apex.model;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * A rental reservation ("order" on the frontend). Contains one or more
 * {@link BookingItem} lines - each line is one vehicle with its own dates,
 * city and service type (self-drive / with driver).
 */
public class Booking {

    /** Money breakdown of a booking. */
    public static class Totals {
        private Integer rental;
        private Integer deposit;
        private Integer homeDelivery;
        private Integer total;

        public Integer getRental() { return rental; }
        public void setRental(Integer rental) { this.rental = rental; }
        public Integer getDeposit() { return deposit; }
        public void setDeposit(Integer deposit) { this.deposit = deposit; }
        public Integer getHomeDelivery() { return homeDelivery; }
        public void setHomeDelivery(Integer homeDelivery) { this.homeDelivery = homeDelivery; }
        public Integer getTotal() { return total; }
        public void setTotal(Integer total) { this.total = total; }
    }

    /** One rented vehicle inside a booking. */
    public static class BookingItem {
        private Integer carId;
        private String start;
        private String end;
        private String city;
        private String service;
        private Integer driverRate;
        private Integer assignedDriver;
        private JsonObject price;
        private JsonObject selfDriver;

        public Integer getCarId() { return carId; }
        public void setCarId(Integer carId) { this.carId = carId; }
        public String getStart() { return start; }
        public void setStart(String start) { this.start = start; }
        public String getEnd() { return end; }
        public void setEnd(String end) { this.end = end; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        public Integer getDriverRate() { return driverRate; }
        public void setDriverRate(Integer driverRate) { this.driverRate = driverRate; }
        public Integer getAssignedDriver() { return assignedDriver; }
        public void setAssignedDriver(Integer assignedDriver) { this.assignedDriver = assignedDriver; }
        public JsonObject getPrice() { return price; }
        public void setPrice(JsonObject price) { this.price = price; }
        public JsonObject getSelfDriver() { return selfDriver; }
        public void setSelfDriver(JsonObject selfDriver) { this.selfDriver = selfDriver; }
    }

    private String id;
    private String userId;
    private String name;
    private String email;
    private String phone;
    private String destination;
    private String pickupMode;
    private String homeAddress;
    private String officeAddress;
    private String identityType;
    private String identityMasked;
    private String identityStatus;
    private String payment;
    private String status;
    private Integer paid;
    private Integer depositPaid;
    private Integer cancellationFee;
    private Boolean ownerPayoutDone;
    private String created;
    private Totals totals;
    private List<BookingItem> items;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getPickupMode() { return pickupMode; }
    public void setPickupMode(String pickupMode) { this.pickupMode = pickupMode; }
    public String getHomeAddress() { return homeAddress; }
    public void setHomeAddress(String homeAddress) { this.homeAddress = homeAddress; }
    public String getOfficeAddress() { return officeAddress; }
    public void setOfficeAddress(String officeAddress) { this.officeAddress = officeAddress; }
    public String getIdentityType() { return identityType; }
    public void setIdentityType(String identityType) { this.identityType = identityType; }
    public String getIdentityMasked() { return identityMasked; }
    public void setIdentityMasked(String identityMasked) { this.identityMasked = identityMasked; }
    public String getIdentityStatus() { return identityStatus; }
    public void setIdentityStatus(String identityStatus) { this.identityStatus = identityStatus; }
    public String getPayment() { return payment; }
    public void setPayment(String payment) { this.payment = payment; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPaid() { return paid; }
    public void setPaid(Integer paid) { this.paid = paid; }
    public Integer getDepositPaid() { return depositPaid; }
    public void setDepositPaid(Integer depositPaid) { this.depositPaid = depositPaid; }
    public Integer getCancellationFee() { return cancellationFee; }
    public void setCancellationFee(Integer cancellationFee) { this.cancellationFee = cancellationFee; }
    public Boolean getOwnerPayoutDone() { return ownerPayoutDone; }
    public void setOwnerPayoutDone(Boolean ownerPayoutDone) { this.ownerPayoutDone = ownerPayoutDone; }
    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }
    public Totals getTotals() { return totals; }
    public void setTotals(Totals totals) { this.totals = totals; }
    public List<BookingItem> getItems() { return items; }
    public void setItems(List<BookingItem> items) { this.items = items; }

    public void validate() {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Renter name is required");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Booking needs at least one vehicle");
    }
}
