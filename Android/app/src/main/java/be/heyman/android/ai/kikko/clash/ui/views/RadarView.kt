package be.heyman.android.ai.kikko.clash.ui.views

import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.clash.data.PlayerCatalogue
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SensorEventListener {

    private val TAG = "RadarView"
    private val TURTLE_ICON_SIZE_DP = 36f
    private val CARDINAL_POINTS_RADIUS_OFFSET_DP = 25f
    private val DISTANCE_LABEL_VERTICAL_OFFSET_DP = 7f
    private val PLAYER_NAME_VERTICAL_OFFSET_DP = 12f

    private val turtleIconSizePx: Int
    private val cardinalPointsRadiusOffsetPx: Float
    private val distanceLabelVerticalOffsetPx: Float
    private val playerNameVerticalOffsetPx: Float

    private val playerTextPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardinalPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val turtleBitmap: Bitmap

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var smoothedAccelerometerReading = FloatArray(3)
    private var smoothedMagnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var azimuth: Float = 0f
    private var players: Map<String, PlayerCatalogue> = emptyMap()
    private var userLocation: Location? = null
    private val maxDistanceMeters = 20f
    private val cardinalPoints = mapOf("N" to 0f, "E" to 90f, "S" to 180f, "O" to 270f)
    private val alpha: Float = 0.1f

    init {
        val density = context.resources.displayMetrics.density
        turtleIconSizePx = (TURTLE_ICON_SIZE_DP * density).toInt()
        cardinalPointsRadiusOffsetPx = CARDINAL_POINTS_RADIUS_OFFSET_DP * density
        distanceLabelVerticalOffsetPx = DISTANCE_LABEL_VERTICAL_OFFSET_DP * density
        playerNameVerticalOffsetPx = PLAYER_NAME_VERTICAL_OFFSET_DP * density

        val primaryColor = resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
        val onSurfaceColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)

        playerTextPaint.color = onSurfaceColor
        playerTextPaint.textSize = 28f
        playerTextPaint.textAlign = Paint.Align.CENTER

        cardinalPaint.color = primaryColor
        cardinalPaint.textSize = 52f
        cardinalPaint.textAlign = Paint.Align.CENTER
        cardinalPaint.isFakeBoldText = true

        labelPaint.color = onSurfaceColor
        labelPaint.textSize = 30f
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.isFakeBoldText = true

        val turtleDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_turtle)
        turtleBitmap = turtleDrawable?.toBitmap(turtleIconSizePx, turtleIconSizePx, Bitmap.Config.ARGB_8888)!!
    }

    private fun resolveThemeColor(@AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    fun updatePlayers(newPlayers: Map<String, PlayerCatalogue>, currentUserLocation: Location?) {
        this.players = newPlayers
        this.userLocation = currentUserLocation
        invalidate() // Redessine la vue
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(centerX, centerY) * 0.55f

        // Dessin des labels de distance
        val distances = listOf(5f, 10f, 15f, 20f)
        distances.forEach { distance ->
            val circleRadius = (distance / maxDistanceMeters) * radius
            canvas.drawText(
                "${distance.toInt()}m",
                centerX,
                centerY - circleRadius + labelPaint.textSize + distanceLabelVerticalOffsetPx,
                labelPaint
            )
        }

        // Dessin des points cardinaux
        val textRadius = radius + cardinalPointsRadiusOffsetPx
        cardinalPoints.forEach { (point, angle) ->
            val angleOnRadar = (angle - Math.toDegrees(azimuth.toDouble())).toFloat()
            val angleInRad = Math.toRadians(angleOnRadar.toDouble())
            val x = centerX + (textRadius * sin(angleInRad)).toFloat()
            val y = centerY - (textRadius * cos(angleInRad)).toFloat() + (cardinalPaint.textSize / 2)
            canvas.drawText(point, x, y, cardinalPaint)
        }

        // Dessin des joueurs
        val currentUserLoc = userLocation ?: return

        for (player in players.values) {
            val lat = player.latitude
            val lon = player.longitude
            if (lat != null && lon != null) {
                val playerLocation = Location("player").apply { latitude = lat; longitude = lon }
                val distance = currentUserLoc.distanceTo(playerLocation)

                if (distance <= maxDistanceMeters) {
                    val bearing = currentUserLoc.bearingTo(playerLocation)
                    val angleOnRadar = (bearing - Math.toDegrees(azimuth.toDouble())).toFloat()
                    val angleInRad = Math.toRadians(angleOnRadar.toDouble())
                    val distanceOnRadar = (distance / maxDistanceMeters) * radius
                    val playerX = centerX + (distanceOnRadar * sin(angleInRad)).toFloat()
                    val playerY = centerY - (distanceOnRadar * cos(angleInRad)).toFloat()

                    val paint = Paint()
                    paint.colorFilter = PorterDuffColorFilter(player.color, PorterDuff.Mode.SRC_IN)
                    canvas.drawBitmap(turtleBitmap, playerX - turtleBitmap.width / 2, playerY - turtleBitmap.height / 2, paint)
                    canvas.drawText(player.playerName, playerX, playerY + turtleBitmap.height / 2 + playerNameVerticalOffsetPx, playerTextPaint)
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            smoothedAccelerometerReading = lowPass(event.values, smoothedAccelerometerReading)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            smoothedMagnetometerReading = lowPass(event.values, smoothedMagnetometerReading)
        }
        updateOrientationAngles()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun lowPass(input: FloatArray, output: FloatArray): FloatArray {
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
    }

    private fun updateOrientationAngles() {
        SensorManager.getRotationMatrix(rotationMatrix, null, smoothedAccelerometerReading, smoothedMagnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        azimuth = orientationAngles[0]
        invalidate()
    }
}