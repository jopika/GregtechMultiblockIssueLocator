import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/** Resolves actual production member descriptors with ASM, without loading game classes. */
public final class CheckLinkage {
    private record TypeInfo(String parent, List<String> interfaces, Set<String> fields, Set<String> methods) {}
    private static final Map<String, TypeInfo> TYPES = new HashMap<>();
    private static URLClassLoader inputs;
    private static int checked;

    public static void main(String[] args) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String argument : args) urls.add(Path.of(argument).toUri().toURL());
        // Platform parent avoids accidentally resolving development Minecraft from the verifier's classpath.
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
             ZipFile addon = new ZipFile(args[0])) {
            inputs = loader;
            var entries = addon.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream stream = addon.getInputStream(entry)) {
                    new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                                   String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                                       String desc, boolean isInterface) {
                                    verify(owner, method, desc, true);
                                }
                                @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                                    verify(owner, field, desc, false);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        System.out.println("Resolved " + checked + " production method/field references against SRG Forge and pinned mods (no game launch).");
    }

    private static void verify(String owner, String name, String descriptor, boolean method) {
        if (!owner.startsWith("net/minecraft/") && !owner.startsWith("net/minecraftforge/")
                && !owner.startsWith("com/mojang/") && !owner.startsWith("com/gregtechceu/")
                && !owner.startsWith("com/lowdragmc/")
                && !owner.startsWith("io/github/jopika/gtmultiblocklocator/")) return;
        if (!resolve(owner, name + descriptor, method, new HashSet<>())) {
            throw new AssertionError("Unresolved production member: " + owner + "." + name + descriptor);
        }
        checked++;
    }

    private static boolean resolve(String owner, String member, boolean method, Set<String> visited) {
        if (owner == null || !visited.add(owner)) return false;
        TypeInfo type = type(owner);
        if ((method ? type.methods : type.fields).contains(member)) return true;
        if (member.startsWith("<init>")) return false;
        if (resolve(type.parent, member, method, visited)) return true;
        for (String iface : type.interfaces) if (resolve(iface, member, method, visited)) return true;
        return false;
    }

    private static TypeInfo type(String owner) {
        return TYPES.computeIfAbsent(owner, key -> {
            try (InputStream stream = inputs.getResourceAsStream(key + ".class")) {
                if (stream == null) throw new AssertionError("Missing production class: " + key);
                ClassReader reader = new ClassReader(stream);
                Set<String> fields = new HashSet<>(), methods = new HashSet<>();
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public FieldVisitor visitField(int access, String name, String descriptor,
                                                             String signature, Object value) {
                        fields.add(name + descriptor);
                        return null;
                    }
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        methods.add(name + descriptor);
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return new TypeInfo(reader.getSuperName(), List.of(reader.getInterfaces()), fields, methods);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
