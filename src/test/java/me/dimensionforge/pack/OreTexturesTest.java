package me.dimensionforge.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreTexturesTest {

    private static void png(File f, int w, int h) throws IOException {
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", f);
    }

    @Test
    void singleAndSidedAndInvalid(@TempDir File dir) throws IOException {
        png(new File(dir, "mithril.png"), 16, 16);
        png(new File(dir, "log_side.png"), 16, 16);
        png(new File(dir, "log_top.png"), 16, 16);
        png(new File(dir, "tall.png"), 16, 32);                 // no cuadrada y sin mcmeta -> error
        png(new File(dir, "anim.png"), 16, 64);                 // tira vertical con mcmeta -> válida
        Files.writeString(new File(dir, "anim.png.mcmeta").toPath(), "{\"animation\":{}}");
        Files.writeString(new File(dir, "fake.png").toPath(), "no soy un png");
        png(new File(dir, "Mala Textura.png"), 16, 16);        // nombre inválido

        OreTextures.Scan s = OreTextures.scan(dir);
        List<String> ids = s.entries.stream().map(OreTextures.Entry::id).toList();
        assertTrue(ids.contains("mithril"));
        assertTrue(ids.contains("log"));
        assertTrue(ids.contains("anim"));
        assertFalse(ids.contains("tall"));
        assertFalse(ids.contains("fake"));
        assertFalse(ids.contains("log_top"), "las caras no son menas aparte");
        assertEquals(3, ids.size());
        assertEquals(3, s.errors.size(), s.errors.toString());

        OreTextures.Entry m = s.entries.stream().filter(e -> e.id().equals("mithril")).findFirst().orElseThrow();
        assertTrue(OreTextures.modelJson(m).contains("minecraft:block/cube_all"));
        assertTrue(OreTextures.modelJson(m).contains("dimensionforge:block/ore/mithril"));
        OreTextures.Entry l = s.entries.stream().filter(e -> e.id().equals("log")).findFirst().orElseThrow();
        String lj = OreTextures.modelJson(l);
        assertTrue(lj.contains("cube_bottom_top"));
        assertTrue(lj.contains("dimensionforge:block/ore/log_side") && lj.contains("dimensionforge:block/ore/log_top"));

        Map<String, byte[]> files = new LinkedHashMap<>();
        for (OreTextures.Entry e : s.entries) {
            OreTextures.addFiles(e, files);
        }
        assertTrue(files.containsKey("assets/dimensionforge/textures/block/ore/mithril.png"));
        assertTrue(files.containsKey("assets/dimensionforge/textures/block/ore/anim.png.mcmeta"));
        assertTrue(files.containsKey("assets/dimensionforge/textures/block/ore/log_side.png"));
    }
}
