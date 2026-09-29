package com.apex.service;

import com.apex.config.AppConfig;
import com.apex.dao.*;
import com.apex.db.Database;
import com.apex.model.Booking;
import com.apex.model.Car;
import com.apex.model.Driver;
import com.apex.model.User;
import com.apex.util.Json;
import com.apex.web.ApiException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.List;

public final class SeedService {
    private final Database db; private final UserRepo users; private final CarRepo cars;
    private final DriverRepo drivers; private final BookingRepo bookings; private final ReviewRepo reviews;
    private final SettingsRepo settings; private final AppConfig cfg; private final SecureRandom random = new SecureRandom();
    public SeedService(Database db, UserRepo users, CarRepo cars, DriverRepo drivers, BookingRepo bookings, ReviewRepo reviews, SettingsRepo settings, AppConfig cfg) {
        this.db=db; this.users=users; this.cars=cars; this.drivers=drivers; this.bookings=bookings; this.reviews=reviews; this.settings=settings; this.cfg=cfg;
    }
    public void run() { int cleaned=users.hardenExistingPasswords(); if(cleaned>0) com.apex.log.Logger.info("Hardened credentials for {} existing account(s)",cleaned); ensureAdmin(); seedFleetIfEmpty(); seedDemoReviewsIfEmpty(); }
    private void ensureAdmin() { db.tx(c->{ User existing=users.findAdmin(c); if(existing!=null){com.apex.log.Logger.info("Admin account '{}' already present - skipping",existing.username);return null;} if(users.findByLogin(c,cfg.adminUsername)!=null||users.get(c,"UADMIN")!=null) throw ApiException.server("Admin seed username or ID is already used by a non-admin account"); String id="UADMIN"; JsonObject data=new JsonObject(); data.addProperty("id",id);data.addProperty("username",cfg.adminUsername);data.addProperty("email","admin@apex.local");data.addProperty("name","APEX Admin");data.addProperty("phone","");data.addProperty("cnic","");data.addProperty("role","admin"); User u=new User(id,cfg.adminUsername,"admin@apex.local","","APEX Admin","","admin",BCrypt.hashpw(cfg.adminPassword,BCrypt.gensalt(10)),AuthService.nowIso(),data); users.insert(c,u,u.passwordHash); com.apex.log.Logger.info("Seeded admin account '{}'",cfg.adminUsername);return null;}); }
    private void seedFleetIfEmpty() { if(cars.count()>0){com.apex.log.Logger.info("Fleet already present ({} cars) - skipping seed.json",cars.count());return;} Path seed=Paths.get(cfg.home,"seed.json"); if(!Files.isRegularFile(seed)){com.apex.log.Logger.warn("seed.json not found at {} - starting with an empty fleet",seed);return;} JsonObject s=Json.readFile(seed); db.tx(c->{int nc=0,nd=0; if(s.has("fleet")&&s.get("fleet").isJsonArray())for(JsonElement e:s.getAsJsonArray("fleet")){if(!e.isJsonObject())continue;Car car=Car.fromJson(e.getAsJsonObject());if(car==null||car.name.isEmpty())continue;cars.upsert(c,car);nc++;} if(s.has("drivers")&&s.get("drivers").isJsonArray())for(JsonElement e:s.getAsJsonArray("drivers")){if(!e.isJsonObject())continue;Driver d=Driver.fromJson(e.getAsJsonObject());if(d==null||d.name.isEmpty())continue;drivers.upsert(c,d);nd++;} if(s.has("config")&&s.get("config").isJsonObject())settings.setConfig(c,s.getAsJsonObject("config"));com.apex.log.Logger.info("Seeded fleet from seed.json: {} cars, {} drivers",nc,nd);return null;}); }
    private void seedDemoReviewsIfEmpty() { try { db.tx(c->{if(reviews.any(c))return null; List<Car> fleet=cars.list(c); if(fleet.isEmpty()){com.apex.log.Logger.info("No fleet yet - skipping demo reviews");return null;} Object[][] d={{"UDEMO1","hassan.demo","hassan.demo@apex.local","Hassan Ali",5,"Smooth self-drive booking from start to finish. The car was spotless and handover at the office took only a few minutes. Will rent again."},{"UDEMO2","sara.demo","sara.demo@apex.local","Sara Ahmed",5,"Booked with a chauffeur for a family trip - the driver was punctual, polite and knew the routes well. Transparent pricing, no surprises."},{"UDEMO3","bilal.demo","bilal.demo@apex.local","Bilal Khan",4,"Home delivery made pickup effortless and the car was in great condition. Only wish there were more evening delivery slots."}}; int seeded=0; for(int i=0;i<d.length;i++){String id=(String)d[i][0],un=(String)d[i][1],email=(String)d[i][2],name=(String)d[i][3],body=(String)d[i][5];int rating=(Integer)d[i][4];Car car=fleet.get(i%fleet.size()); if(users.get(c,id)==null&&users.findByLogin(c,un)==null){String pw="ApexDemo#"+(10000+random.nextInt(89999))+"!";JsonObject uo=new JsonObject();uo.addProperty("id",id);uo.addProperty("username",un);uo.addProperty("email",email);uo.addProperty("name",name);uo.addProperty("role","customer");User u=new User(id,un,email,"",name,"","customer",BCrypt.hashpw(pw,BCrypt.gensalt(10)),AuthService.nowIso(),uo);users.insert(c,u,u.passwordHash);} String bid="VR-DEMO-REV"+(i+1); if(bookings.get(c,bid)==null){java.time.LocalDate end=java.time.LocalDate.now().minusDays(10+i),start=end.minusDays(3);long rental=Math.max(car.rate,1)*3;JsonObject item=new JsonObject();item.addProperty("carId",car.id);item.addProperty("start",start.toString());item.addProperty("end",end.toString());item.addProperty("startDt",start+"T10:00");item.addProperty("endDt",end+"T10:00");item.addProperty("city","Lahore");item.addProperty("service",i==1?"With driver":"Self-drive");JsonArray items=new JsonArray();items.add(item);JsonObject totals=new JsonObject();totals.addProperty("rental",rental);totals.addProperty("deposit",0);totals.addProperty("homeDelivery",0);totals.addProperty("total",rental);JsonObject b=new JsonObject();b.addProperty("id",bid);b.addProperty("userId",id);b.addProperty("name",name);b.addProperty("email",email);b.addProperty("phone","");b.addProperty("destination","Lahore");b.addProperty("pickupMode","Office pickup");b.addProperty("payment","Cash on pickup");b.addProperty("status","Completed");b.addProperty("paymentStatus","Verified");b.addProperty("startDt",start+"T10:00");b.addProperty("endDt",end+"T10:00");b.add("items",items);b.add("totals",totals);bookings.upsert(c,Booking.fromJson(b));} if(!reviews.existsForBooking(c,bid)){long rid=reviews.insert(c,bid,car.id,id,rating,body);reviews.updateStatus(c,rid,"Approved");seeded++;}} if(seeded>0)com.apex.log.Logger.info("Seeded {} demo customer review(s) for the homepage",seeded);return null;}); } catch(RuntimeException e){com.apex.log.Logger.warn("Could not seed demo reviews: {}",e.getMessage());} }
}
