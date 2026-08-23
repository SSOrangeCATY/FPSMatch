package com.ptcrys.fpsmatch.mixin.compat.forge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ldlib2ForgeCompatibility {
    public static final String INITIAL_FACTORIES_MIXIN =
            "com.phasetranscrystal.fpsmatch.mixin.compat.forge.ResourceLocationFactoriesCompatMixin";
    public static final String DEFAULT_NAMESPACE_FACTORY_MIXIN =
            "com.phasetranscrystal.fpsmatch.mixin.compat.forge.ResourceLocationDefaultNamespaceCompatMixin";

    private static final ForgeVersion INITIAL_FACTORIES_VERSION =
            new ForgeVersion(47, 3, 19);
    private static final ForgeVersion DEFAULT_NAMESPACE_FACTORY_VERSION =
            new ForgeVersion(47, 3, 30);
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$"
    );

    private Ldlib2ForgeCompatibility() {
    }

    public static boolean requiresInitialFactories(String forgeVersion) {
        return parse(forgeVersion).compareTo(INITIAL_FACTORIES_VERSION) < 0;
    }

    public static boolean requiresDefaultNamespaceFactory(String forgeVersion) {
        return parse(forgeVersion).compareTo(DEFAULT_NAMESPACE_FACTORY_VERSION) < 0;
    }

    public static Boolean decisionFor(String mixinClassName, String forgeVersion) {
        if (INITIAL_FACTORIES_MIXIN.equals(mixinClassName)) {
            return requiresInitialFactories(forgeVersion);
        }
        if (DEFAULT_NAMESPACE_FACTORY_MIXIN.equals(mixinClassName)) {
            return requiresDefaultNamespaceFactory(forgeVersion);
        }
        return null;
    }

    public static void applyInitialFactories(ClassNode target) {
        addFactoryIfMissing(
                target,
                "fromNamespaceAndPath",
                resourceLocationFactoryDescriptor(
                        target, "Ljava/lang/String;Ljava/lang/String;"
                ),
                "(Ljava/lang/String;Ljava/lang/String;)V",
                2,
                false
        );
        addFactoryIfMissing(
                target,
                "parse",
                resourceLocationFactoryDescriptor(target, "Ljava/lang/String;"),
                "(Ljava/lang/String;)V",
                1,
                false
        );
    }

    public static void applyDefaultNamespaceFactory(ClassNode target) {
        addFactoryIfMissing(
                target,
                "withDefaultNamespace",
                resourceLocationFactoryDescriptor(target, "Ljava/lang/String;"),
                "(Ljava/lang/String;Ljava/lang/String;)V",
                1,
                true
        );
    }

    private static void addFactoryIfMissing(
            ClassNode target,
            String name,
            String descriptor,
            String constructorDescriptor,
            int argumentCount,
            boolean defaultNamespace
    ) {
        if (target.methods.stream().anyMatch(method ->
                method.name.equals(name) && method.desc.equals(descriptor))) {
            return;
        }

        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name,
                descriptor,
                null,
                null
        );
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, target.name));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        if (defaultNamespace) {
            method.instructions.add(new LdcInsnNode("minecraft"));
        }
        for (int argument = 0; argument < argumentCount; argument++) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, argument));
        }
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                target.name,
                "<init>",
                constructorDescriptor,
                false
        ));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = defaultNamespace ? 4 : argumentCount + 2;
        method.maxLocals = argumentCount;
        target.methods.add(method);
    }

    private static String resourceLocationFactoryDescriptor(
            ClassNode target,
            String arguments
    ) {
        return "(" + arguments + ")L" + target.name + ";";
    }

    private static ForgeVersion parse(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Forge version: " + version);
        }
        try {
            return new ForgeVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Forge version: " + version, exception);
        }
    }

    private record ForgeVersion(int major, int minor, int patch)
            implements Comparable<ForgeVersion> {
        @Override
        public int compareTo(ForgeVersion other) {
            int result = Integer.compare(major, other.major);
            if (result == 0) {
                result = Integer.compare(minor, other.minor);
            }
            return result != 0 ? result : Integer.compare(patch, other.patch);
        }
    }
}
