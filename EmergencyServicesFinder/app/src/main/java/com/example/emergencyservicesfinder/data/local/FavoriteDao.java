package com.example.emergencyservicesfinder.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FavoriteDao {
    @Query("SELECT * FROM favorite_places")
    List<FavoritePlaceEntity> getAllFavoritesSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFavorite(FavoritePlaceEntity favorite);

    @Delete
    void deleteFavorite(FavoritePlaceEntity favorite);

    @Query("DELETE FROM favorite_places WHERE placeId = :placeId")
    void deleteById(String placeId);

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_places WHERE placeId = :placeId)")
    boolean isFavorite(String placeId);
}
