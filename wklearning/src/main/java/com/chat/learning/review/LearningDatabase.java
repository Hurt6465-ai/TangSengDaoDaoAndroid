package com.chat.learning.review;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.chat.learning.model.ReviewState;

@Database(entities = {ReviewState.class}, version = 1, exportSchema = false)
public abstract class LearningDatabase extends RoomDatabase {
    public abstract ReviewStateDao reviewStateDao();

    private static volatile LearningDatabase INSTANCE;

    public static LearningDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (LearningDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    LearningDatabase.class,
                                    "wk_learning.db")
                            // 第一版可用；正式上线积累学习记录后，升级版本必须改成 Migration，不能清库。
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
