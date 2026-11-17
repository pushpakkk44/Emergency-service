package com.example.emergencyservicesfinder.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorite_places")
public class FavoritePlaceEntity {
    @PrimaryKey
    @NonNull
    private String placeId;
    @NonNull
    private String name;
    @NonNull
    private String category;
    private double latitude;
    private double longitude;
    @Nullable
    private String phone;
    @Nullable
    private String address;

    public FavoritePlaceEntity(@NonNull String placeId, @NonNull String name, @NonNull String category,
                               double latitude, double longitude, @Nullable String phone, @Nullable String address) {
        this.placeId = placeId;
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.address = address;
    }

    @NonNull
    public String getPlaceId() { return placeId; }
    public void setPlaceId(@NonNull String placeId) { this.placeId = placeId; }

    @NonNull
    public String getName() { return name; }
    public void setName(@NonNull String name) { this.name = name; }

    @NonNull
    public String getCategory() { return category; }
    public void setCategory(@NonNull String category) { this.category = category; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    @Nullable
    public String getPhone() { return phone; }
    public void setPhone(@Nullable String phone) { this.phone = phone; }

    @Nullable
    public String getAddress() { return address; }
    public void setAddress(@Nullable String address) { this.address = address; }
}
