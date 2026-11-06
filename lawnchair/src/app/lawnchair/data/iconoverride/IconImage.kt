package app.lawnchair.data.iconoverride

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class IconImage(@PrimaryKey val id: String, val png: ByteArray)
