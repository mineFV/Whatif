package fr.mine.customnick;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pochette d'album du morceau en cours (mpris:artUrl), chargée en texture, et sa couleur moyenne.
 * - file://...  : fichier local (Firefox / Chromium écrivent la pochette dans /tmp)
 * - http(s)://  : téléchargée (appli desktop YouTube Music, Spotify...)
 * L'image est recadrée au centre, redimensionnée et ses coins arrondis avec un bord lissé.
 * Elle change toute seule quand le morceau change (nouvelle URL).
 */
public final class AlbumArt {
    private AlbumArt() {}

    public static final int SIZE = 256;
    /** Rayon des coins, en proportion de la taille de l'image. */
    private static final float RADIUS_RATIO = 0.16f;
    private static final Identifier ID = Identifier.fromNamespaceAndPath("whatif", "music/album_art");

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "whatif-album-art");
        t.setDaemon(true);
        return t;
    });

    private static volatile String requestedUrl = null;
    private static volatile String loadedUrl = null;
    /** Couleur moyenne (RGB) de la pochette chargée. */
    private static volatile int loadedAverage = 0;

    /** À appeler pendant le rendu : renvoie la texture si elle est prête pour cette URL, sinon null. */
    public static Identifier get(String url) {
        if (url == null || url.isBlank()) return null;
        if (!url.equals(requestedUrl)) {
            requestedUrl = url;
            IO.submit(() -> load(url));
        }
        return url.equals(loadedUrl) ? ID : null;
    }

    /** Couleur moyenne (RGB) de la pochette de cette URL, ou -1 si elle n'est pas (encore) chargée. */
    public static int averageColor(String url) {
        return url != null && url.equals(loadedUrl) ? loadedAverage : -1;
    }

    private static void load(String url) {
        try {
            byte[] bytes;
            if (url.startsWith("file://")) {
                bytes = Files.readAllBytes(Path.of(URI.create(url)));
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "image/jpeg,image/png,image/*;q=0.8")
                    .build();
                HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) return;
                bytes = response.body();
            } else {
                return;
            }

            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes)); // JPEG / PNG
            if (source == null) return; // format non pris en charge (ex : WebP)

            BufferedImage square = resizeSquare(source);
            int average = averageRgb(square);
            applyRoundedMask(square);

            Minecraft.getInstance().execute(() -> upload(url, square, average)); // textures : thread de rendu
        } catch (Exception e) {
            // pas de pochette : le HUD affiche une icône à la place
        }
    }

    /** Recadre au centre en carré et redimensionne en SIZE x SIZE (avec lissage). */
    private static BufferedImage resizeSquare(BufferedImage source) {
        int s = Math.min(source.getWidth(), source.getHeight());
        BufferedImage crop = source.getSubimage((source.getWidth() - s) / 2, (source.getHeight() - s) / 2, s, s);

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(crop, 0, 0, SIZE, SIZE, null);
        g.dispose();
        return out;
    }

    /** Moyenne des couleurs de l'image. */
    private static int averageRgb(BufferedImage img) {
        long r = 0, gr = 0, b = 0;
        int n = 0;
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                int c = img.getRGB(x, y);
                r += (c >> 16) & 0xFF;
                gr += (c >> 8) & 0xFF;
                b += c & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0x2A3236;
        return (int) (r / n) << 16 | (int) (gr / n) << 8 | (int) (b / n);
    }

    /** Arrondit les coins en réglant l'opacité de chaque pixel selon sa couverture (bord lissé). */
    private static void applyRoundedMask(BufferedImage img) {
        float radius = SIZE * RADIUS_RATIO;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float cx = Math.max(radius - (x + 0.5f), (x + 0.5f) - (SIZE - radius));
                float cy = Math.max(radius - (y + 0.5f), (y + 0.5f) - (SIZE - radius));
                if (cx <= 0 || cy <= 0) continue; // hors des coins : opaque
                float d = (float) Math.sqrt(cx * cx + cy * cy) - radius;
                float cov = Math.max(0f, Math.min(1f, 0.5f - d));
                if (cov >= 1f) continue;
                int c = img.getRGB(x, y);
                int a = Math.round(((c >>> 24) & 0xFF) * cov);
                img.setRGB(x, y, (a << 24) | (c & 0xFFFFFF));
            }
        }
    }

    private static void upload(String url, BufferedImage art, int average) {
        if (!url.equals(requestedUrl)) return; // le morceau a changé entre-temps

        NativeImage image = new NativeImage(SIZE, SIZE, false);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setPixel(x, y, art.getRGB(x, y)); // ARGB
            }
        }
        // remplace (et libère) l'ancienne pochette enregistrée sous le même identifiant
        Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "whatif album art", image));
        loadedAverage = average;
        loadedUrl = url;
    }
}
