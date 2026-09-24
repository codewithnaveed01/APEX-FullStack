package com.apex.model;

/** Company / payment settings editable from the admin panel. */
public class SiteConfig {

    private Integer driverRate;
    private Integer overtime;
    private String jazzCashNumber;
    private String easypaisaNumber;
    private String bankAccount;
    private String bankIBAN;
    private String raastId;
    private String officeAddress;
    private Integer homeDeliveryCharge;
    private String companyPhone;

    public Integer getDriverRate() { return driverRate; }
    public void setDriverRate(Integer driverRate) { this.driverRate = driverRate; }
    public Integer getOvertime() { return overtime; }
    public void setOvertime(Integer overtime) { this.overtime = overtime; }
    public String getJazzCashNumber() { return jazzCashNumber; }
    public void setJazzCashNumber(String jazzCashNumber) { this.jazzCashNumber = jazzCashNumber; }
    public String getEasypaisaNumber() { return easypaisaNumber; }
    public void setEasypaisaNumber(String easypaisaNumber) { this.easypaisaNumber = easypaisaNumber; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public String getBankIBAN() { return bankIBAN; }
    public void setBankIBAN(String bankIBAN) { this.bankIBAN = bankIBAN; }
    public String getRaastId() { return raastId; }
    public void setRaastId(String raastId) { this.raastId = raastId; }
    public String getOfficeAddress() { return officeAddress; }
    public void setOfficeAddress(String officeAddress) { this.officeAddress = officeAddress; }
    public Integer getHomeDeliveryCharge() { return homeDeliveryCharge; }
    public void setHomeDeliveryCharge(Integer homeDeliveryCharge) { this.homeDeliveryCharge = homeDeliveryCharge; }
    public String getCompanyPhone() { return companyPhone; }
    public void setCompanyPhone(String companyPhone) { this.companyPhone = companyPhone; }
}
