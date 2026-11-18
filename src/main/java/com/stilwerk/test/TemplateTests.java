package com.stilwerk.test;

import com.stilwerk.Stilwerk;
import com.stilwerk.swap.SwapMapping;
import com.stilwerk.swap.Target;
import com.stilwerk.swap.Template;
import com.stilwerk.swap.Templates;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stilwerk.MODID)
@PrefixGameTestTemplate(false)
public final class TemplateTests {
    private static final String T = StilwerkGameTests.T;

    static ResourceLocation mc(String p) {
        return ResourceLocation.withDefaultNamespace(p);
    }

    static Template builtIn(String id) {
        return Templates.builtIn().stream().filter(t -> t.id().equals("stilwerk:" + id)).findFirst().orElseThrow();
    }

    @GameTest(template = T)
    public static void woodSteps(GameTestHelper h) {
        Template darken = builtIn("darken_wood");
        h.assertTrue(darken.mapping().get(mc("oak")).equals(Target.family(mc("jungle"))), "oak darkens to jungle");
        h.assertTrue(!darken.mapping().containsKey(mc("dark_oak")), "dark oak is the darkest");
        h.assertTrue(builtIn("lighten_wood").mapping().get(mc("spruce")).equals(Target.family(mc("cherry"))), "spruce lightens to cherry");
        h.assertTrue(Templates.builtIn().size() == 10, "10 built-in templates");
        for (Template t : Templates.builtIn()) h.assertTrue(!t.mapping().isEmpty(), t.id() + " survived validation");
        h.succeed();
    }

    @GameTest(template = T)
    public static void appliesOnlyPresentFamilies(GameTestHelper h) {
        SwapMapping m = builtIn("japanese_to_nordic").applyTo(SwapMapping.EMPTY, List.of(mc("oak"), mc("stone_bricks")));
        h.assertTrue(m.families().equals(Map.of(mc("oak"), Target.family(mc("spruce")))), "only oak mapped: " + m.families());
        h.succeed();
    }

    @GameTest(template = T)
    public static void userTemplateRoundTrip(GameTestHelper h) {
        try {
            Path dir = Files.createTempDirectory("stilwerk-templates");
            Templates.dirOverride = dir;
            Template t = new Template("user:mein_stil", "Mein Stil", Map.of(mc("oak"), Target.family(mc("birch")), mc("bricks"), Target.mix("stilwerk:old_mossy")));
            Templates.save(t);
            List<Template> user = Templates.user();
            h.assertTrue(user.size() == 1 && user.get(0).equals(t), "saved template reads back: " + user);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            Templates.dirOverride = null;
        }
        h.succeed();
    }
}
