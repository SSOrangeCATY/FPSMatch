package com.phasetranscrystal.fpsmatch.mixin.compat.forge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ldlib2ForgeCompatibilityTest {

    @Test
    void initialFactoriesAreAddedOnlyWhenMissing() {
        ClassNode target = resourceLocationClass();
        target.methods.add(method(
                "parse",
                "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"
        ));

        Ldlib2ForgeCompatibility.applyInitialFactories(target);
        Ldlib2ForgeCompatibility.applyInitialFactories(target);

        assertEquals(1, methods(target, "fromNamespaceAndPath").size());
        assertEquals(1, methods(target, "parse").size());
    }

    @Test
    void defaultNamespaceFactoryIsAddedOnlyWhenMissing() {
        ClassNode target = resourceLocationClass();

        Ldlib2ForgeCompatibility.applyDefaultNamespaceFactory(target);
        Ldlib2ForgeCompatibility.applyDefaultNamespaceFactory(target);

        assertEquals(1, methods(target, "withDefaultNamespace").size());
    }

    @Test
    void generatedFactoriesArePublicStaticAndCallLegacyConstructors() {
        ClassNode target = resourceLocationClass();
        Ldlib2ForgeCompatibility.applyInitialFactories(target);
        Ldlib2ForgeCompatibility.applyDefaultNamespaceFactory(target);

        MethodNode fromNamespace = onlyMethod(target, "fromNamespaceAndPath");
        MethodNode parse = onlyMethod(target, "parse");
        MethodNode withDefault = onlyMethod(target, "withDefaultNamespace");

        assertPublicStatic(fromNamespace);
        assertPublicStatic(parse);
        assertPublicStatic(withDefault);
        assertConstructorCall(fromNamespace,
                "(Ljava/lang/String;Ljava/lang/String;)V");
        assertConstructorCall(parse, "(Ljava/lang/String;)V");
        assertConstructorCall(withDefault,
                "(Ljava/lang/String;Ljava/lang/String;)V");
        assertTrue(instructions(withDefault).stream()
                .filter(LdcInsnNode.class::isInstance)
                .map(LdcInsnNode.class::cast)
                .anyMatch(instruction -> "minecraft".equals(instruction.cst)));
    }

    @Test
    void bothCompatibilityMixinsAreDeclaredInTheCommonMixinList()
            throws IOException {
        String config = Files.readString(
                Path.of("src/main/resources/fpsmatch.mixins.json")
        );

        assertTrue(config.contains(
                "\"compat.forge.ResourceLocationFactoriesCompatMixin\""
        ));
        assertTrue(config.contains(
                "\"compat.forge.ResourceLocationDefaultNamespaceCompatMixin\""
        ));
    }

    private static ClassNode resourceLocationClass() {
        ClassNode target = new ClassNode();
        target.name = "net/minecraft/resources/ResourceLocation";
        target.methods = new java.util.ArrayList<>();
        return target;
    }

    private static MethodNode method(String name, String descriptor) {
        return new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name, descriptor, null, null);
    }

    private static List<MethodNode> methods(ClassNode target, String name) {
        return target.methods.stream()
                .filter(method -> method.name.equals(name))
                .toList();
    }

    private static MethodNode onlyMethod(ClassNode target, String name) {
        List<MethodNode> methods = methods(target, name);
        assertEquals(1, methods.size(), name);
        return methods.get(0);
    }

    private static void assertPublicStatic(MethodNode method) {
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE
                        | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC)
        );
    }

    private static void assertConstructorCall(MethodNode method, String descriptor) {
        assertTrue(instructions(method).stream()
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .anyMatch(instruction -> instruction.getOpcode() == Opcodes.INVOKESPECIAL
                        && instruction.owner.equals(
                        "net/minecraft/resources/ResourceLocation"
                )
                        && instruction.name.equals("<init>")
                        && instruction.desc.equals(descriptor)));
    }

    @Test
    void generatedDescriptorsFollowTheRuntimeTargetName() {
        ClassNode target = resourceLocationClass();
        target.name = "runtime/obfuscated/ResourceLocation";

        Ldlib2ForgeCompatibility.applyInitialFactories(target);

        MethodNode method = onlyMethod(target, "fromNamespaceAndPath");
        assertEquals(
                "(Ljava/lang/String;Ljava/lang/String;)Lruntime/obfuscated/ResourceLocation;",
                method.desc
        );
        assertTrue(instructions(method).stream()
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .anyMatch(instruction -> target.name.equals(instruction.owner)));
    }

    private static List<AbstractInsnNode> instructions(MethodNode method) {
        return java.util.Arrays.asList(method.instructions.toArray());
    }
}
