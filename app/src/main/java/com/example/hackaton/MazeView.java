package com.example.hackaton;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

public class MazeView extends View {

    public interface Listener {
        void onNearWall();
        void onWin();
    }

    private Listener listener;

    // Tamaño del laberinto (más grande = más difícil)
    private static final int COLS = 7;
    private static final int ROWS = 11;

    // Paredes: vW[r][c] = pared vertical a la izquierda de la celda (r,c)
    //          hW[r][c] = pared horizontal arriba de la celda (r,c)
    private boolean[][] vW = new boolean[ROWS][COLS + 1];
    private boolean[][] hW = new boolean[ROWS + 1][COLS];
    private final ArrayList<float[]> segs = new ArrayList<>();
    private final Random random = new Random();

    private float cell, offX, offY, radius, wallHalf;
    private float x, y, vx, vy;
    private float gx, gy;
    private boolean won = false;
    private boolean nearWall = false;
    private long lastNearWall = 0;

    private static final int TRAIL = 14;
    private final float[] trailX = new float[TRAIL];
    private final float[] trailY = new float[TRAIL];
    private int trailHead = 0;

    private Bitmap mazeBitmap;
    private final RectF rect = new RectF();
    private final Path starPath = new Path();

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RadialGradient glowNormal, glowWarning;

    public MazeView(Context context) {
        super(context);
        init();
    }

    public MazeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        starPaint.setColor(Color.parseColor("#FFD54F"));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        cell = Math.min((float) w / COLS, (float) h / ROWS);
        offX = (w - cell * COLS) / 2f;
        offY = (h - cell * ROWS) / 2f;
        radius = cell * 0.26f;
        wallHalf = cell * 0.035f;

        glowNormal = new RadialGradient(0, 0, radius * 2.6f,
                new int[]{0xAA40C4FF, 0x0040C4FF}, null, Shader.TileMode.CLAMP);
        glowWarning = new RadialGradient(0, 0, radius * 2.6f,
                new int[]{0xCCFF5252, 0x00FF5252}, null, Shader.TileMode.CLAMP);
        ballPaint.setShader(new RadialGradient(-radius * 0.3f, -radius * 0.3f, radius * 1.4f,
                new int[]{0xFFFFFFFF, 0xFF80D8FF, 0xFF2979FF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        goalGlowPaint.setShader(new RadialGradient(0, 0, cell * 0.7f,
                new int[]{0xFFFFD54F, 0x66FFC107, 0x00FFC107},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        textPaint.setTextSize(cell * 0.9f);

        buildStar();
        newMaze();
    }

    // ---------- Generación del laberinto ----------

    /** Genera un laberinto nuevo y reinicia la pelota. */
    public void newMaze() {
        if (cell == 0) return;
        generateMaze();
        buildSegments();
        buildBitmap(getWidth(), getHeight());
        resetBall();
    }

    /** Backtracker recursivo (iterativo con pila): siempre hay solución. */
    private void generateMaze() {
        for (boolean[] row : vW) java.util.Arrays.fill(row, true);
        for (boolean[] row : hW) java.util.Arrays.fill(row, true);

        boolean[][] visited = new boolean[ROWS][COLS];
        ArrayList<int[]> stack = new ArrayList<>();
        stack.add(new int[]{0, 0});
        visited[0][0] = true;

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        while (!stack.isEmpty()) {
            int[] cur = stack.get(stack.size() - 1);
            int r = cur[0], c = cur[1];

            ArrayList<int[]> options = new ArrayList<>();
            for (int[] d : dirs) {
                int nr = r + d[0], nc = c + d[1];
                if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && !visited[nr][nc]) {
                    options.add(new int[]{nr, nc, d[0], d[1]});
                }
            }

            if (options.isEmpty()) {
                stack.remove(stack.size() - 1);
                continue;
            }

            int[] o = options.get(random.nextInt(options.size()));
            int nr = o[0], nc = o[1];
            if (o[2] == -1) hW[r][c] = false;          // arriba
            else if (o[2] == 1) hW[r + 1][c] = false;  // abajo
            else if (o[3] == -1) vW[r][c] = false;     // izquierda
            else vW[r][c + 1] = false;                 // derecha

            visited[nr][nc] = true;
            stack.add(new int[]{nr, nc});
        }
    }

    private void buildSegments() {
        segs.clear();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c <= COLS; c++) {
                if (vW[r][c]) {
                    float px = offX + c * cell;
                    segs.add(new float[]{px, offY + r * cell, px, offY + (r + 1) * cell});
                }
            }
        }
        for (int r = 0; r <= ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (hW[r][c]) {
                    float py = offY + r * cell;
                    segs.add(new float[]{offX + c * cell, py, offX + (c + 1) * cell, py});
                }
            }
        }
    }

    private void buildStar() {
        starPath.reset();
        float outer = cell * 0.3f, inner = cell * 0.13f;
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            float rad = (i % 2 == 0) ? outer : inner;
            float px = (float) Math.cos(ang) * rad;
            float py = (float) Math.sin(ang) * rad;
            if (i == 0) starPath.moveTo(px, py);
            else starPath.lineTo(px, py);
        }
        starPath.close();
    }

    // ---------- Dibujo estático (una sola vez por laberinto) ----------

    private void buildBitmap(int w, int h) {
        mazeBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(mazeBitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Fondo degradado
        p.setShader(new LinearGradient(0, 0, 0, h,
                Color.parseColor("#0B0D26"), Color.parseColor("#1B1C4D"), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // Suelo con patrón de cuadros
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                p.setColor(((r + col) % 2 == 0) ? Color.parseColor("#15184A") : Color.parseColor("#1A1E58"));
                float l = offX + col * cell;
                float t = offY + r * cell;
                rect.set(l, t, l + cell, t + cell);
                c.drawRect(rect, p);
            }
        }

        // Portal de inicio (celda 0,0)
        float sx = offX + 0.5f * cell, sy = offY + 0.5f * cell;
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Color.parseColor("#69F0AE"));
        ring.setStrokeWidth(cell * 0.04f);
        ring.setAlpha(230);
        c.drawCircle(sx, sy, cell * 0.36f, ring);
        ring.setAlpha(120);
        c.drawCircle(sx, sy, cell * 0.26f, ring);
        ring.setAlpha(60);
        c.drawCircle(sx, sy, cell * 0.16f, ring);

        // Paredes: un solo Path con todos los segmentos
        Path walls = new Path();
        for (float[] s : segs) {
            walls.moveTo(s[0], s[1]);
            walls.lineTo(s[2], s[3]);
        }

        Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        wp.setStyle(Paint.Style.STROKE);
        wp.setStrokeCap(Paint.Cap.ROUND);
        wp.setStrokeJoin(Paint.Join.ROUND);

        // 1) Resplandor
        wp.setStrokeWidth(cell * 0.14f);
        wp.setColor(Color.parseColor("#7C4DFF"));
        wp.setAlpha(150);
        wp.setMaskFilter(new BlurMaskFilter(cell * 0.14f, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(walls, wp);

        // 2) Cuerpo con degradado
        wp.setMaskFilter(null);
        wp.setAlpha(255);
        wp.setStrokeWidth(wallHalf * 2f);
        wp.setShader(new LinearGradient(offX, offY, offX + cell * COLS, offY + cell * ROWS,
                Color.parseColor("#448AFF"), Color.parseColor("#E040FB"), Shader.TileMode.CLAMP));
        c.drawPath(walls, wp);

        // 3) Brillo central fino
        wp.setShader(null);
        wp.setStrokeWidth(Math.max(1f, cell * 0.014f));
        wp.setColor(0xCCFFFFFF);
        c.drawPath(walls, wp);
    }

    // ---------- Juego ----------

    public void resetBall() {
        x = offX + 0.5f * cell;
        y = offY + 0.5f * cell;
        gx = offX + (COLS - 0.5f) * cell;
        gy = offY + (ROWS - 0.5f) * cell;
        vx = 0;
        vy = 0;
        won = false;
        nearWall = false;
        for (int i = 0; i < TRAIL; i++) {
            trailX[i] = x;
            trailY[i] = y;
        }
        invalidate();
    }

    /** Distancia del punto a la pared (línea) más cercana. */
    private float distanceToWalls(float px, float py) {
        float best = Float.MAX_VALUE;
        for (float[] s : segs) {
            float nx = clamp(px, Math.min(s[0], s[2]), Math.max(s[0], s[2]));
            float ny = clamp(py, Math.min(s[1], s[3]), Math.max(s[1], s[3]));
            float dx = px - nx, dy = py - ny;
            float d = dx * dx + dy * dy;
            if (d < best) best = d;
        }
        return (float) Math.sqrt(best);
    }

    public void onTilt(float ax, float ay, float dt) {
        if (won || cell == 0) return;

        float gain = cell * 1.5f;
        float maxSpeed = cell * 8f;
        float damping = 1f - 3f * dt;
        float minDist = radius + wallHalf;

        vx = clamp((vx + ax * gain * dt) * damping, -maxSpeed, maxSpeed);
        vy = clamp((vy + ay * gain * dt) * damping, -maxSpeed, maxSpeed);

        float nx = x + vx * dt;
        if (distanceToWalls(nx, y) >= minDist) x = nx;
        else vx = -vx * 0.3f;

        float ny = y + vy * dt;
        if (distanceToWalls(x, ny) >= minDist) y = ny;
        else vy = -vy * 0.3f;

        trailX[trailHead] = x;
        trailY[trailHead] = y;
        trailHead = (trailHead + 1) % TRAIL;

        // Cerca de una pared -> vibrar y marcar en rojo
        float gap = distanceToWalls(x, y) - minDist;
        nearWall = gap < cell * 0.06f;
        if (nearWall) {
            long now = System.currentTimeMillis();
            if (now - lastNearWall > 80) {
                lastNearWall = now;
                if (listener != null) listener.onNearWall();
            }
        }

        // Meta
        if (Math.hypot(x - gx, y - gy) < cell * 0.35f) {
            won = true;
            nearWall = false;
            if (listener != null) listener.onWin();
        }

        invalidate();
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mazeBitmap == null) return;
        canvas.drawBitmap(mazeBitmap, 0, 0, bitmapPaint);

        // Meta: resplandor pulsante + estrella giratoria
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        float spin = (System.currentTimeMillis() % 6000) / 6000f * 360f;
        canvas.save();
        canvas.translate(gx, gy);
        float s = 0.85f + 0.3f * pulse;
        canvas.scale(s, s);
        canvas.drawCircle(0, 0, cell * 0.7f, goalGlowPaint);
        canvas.restore();
        canvas.save();
        canvas.translate(gx, gy);
        canvas.rotate(spin);
        canvas.drawPath(starPath, starPaint);
        canvas.restore();

        // Estela
        for (int i = 0; i < TRAIL; i++) {
            int idx = (trailHead + i) % TRAIL;
            float frac = (i + 1f) / TRAIL;
            trailPaint.setColor(Color.argb((int) (80 * frac), 64, 196, 255));
            canvas.drawCircle(trailX[idx], trailY[idx], radius * 0.85f * frac, trailPaint);
        }

        // Resplandor + pelota
        canvas.save();
        canvas.translate(x, y);
        glowPaint.setShader(nearWall ? glowWarning : glowNormal);
        canvas.drawCircle(0, 0, radius * 2.6f, glowPaint);
        canvas.drawCircle(0, 0, radius, ballPaint);
        canvas.restore();

        if (won) {
            canvas.drawColor(0x99000000);
            canvas.drawText("¡Ganaste!", getWidth() / 2f, getHeight() / 2f, textPaint);
        }

        postInvalidateOnAnimation();
    }
}