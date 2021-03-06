/*
 * Copyright 2010 Jonathan Feinberg
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package jycessing.build;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import processing.core.PApplet;

@SuppressWarnings("serial")
public class DriverGenerator {
    final String BAD_METHOD = "^(" //
            + "init|handleDraw|draw|parse[A-Z].*"
            + "|arraycopy|openStream|str|.*Pressed|.*Released"
            + "|(un)?register[A-Z].*|print(ln)?|setup[A-Z].+|thread" //
            + "|(get|set|remove)Cache|max|update|destroy|main|flush" //
            + "|addListeners|dataFile|die|setup|mouseE(ntered|xited)" //
            + "|paint|sketch[A-Z].*|stop|save(File|Path)|displayable|method" //
            + "|runSketch|start|focus(Lost|Gained)" //
            + ")$";

    private static final Set<String> BAD_FIELDS = new HashSet<String>(
            Arrays.asList("screen", "args", "recorder", "frame", "g",
                    "selectedFile", "keyEvent", "mouseEvent", "sketchPath",
                    "screenWidth", "screenHeight", "defaultSize", "firstMouse",
                    "finished", "requestImageMax"));

    private static final Set<String> ALL_APPLET_METHODS = Collections
            .unmodifiableSet(new HashSet<String>() {
                {
                    for (final Method m : PApplet.class.getDeclaredMethods()) {
                        if (!Modifier.isPublic(m.getModifiers())) {
                            continue;
                        }
                        add(m.getName());
                    }
                }
            });

    private static final Set<String> PYTHON_BUILTINS = new HashSet<String>(
            Arrays.asList("map", "filter", "set", "str"));

    final Map<String, Binding> bindings = new HashMap<String, Binding>();

    public DriverGenerator() {
        for (final Method m : PApplet.class.getDeclaredMethods()) {
            maybeAdd(m);
        }
        for (final Field f : PApplet.class.getDeclaredFields()) {
            maybeAdd(f);
        }
    }

    private Binding findOrCreateBinding(final String name) {
        if (!bindings.containsKey(name)) {
            bindings.put(name,
                    new Binding(name, PYTHON_BUILTINS.contains(name)));
        }
        return bindings.get(name);
    }

    private void maybeAdd(final Method m) {
        final String name = m.getName();
        final int mods = m.getModifiers();
        if (!Modifier.isPublic(mods) || !PolymorphicMethod.shouldAdd(m)
                || name.matches(BAD_METHOD)
                || !ALL_APPLET_METHODS.contains(name)) {
            return;
        }
        findOrCreateBinding(name).add(m);
    }

    private void maybeAdd(final Field f) {
        final String name = f.getName();
        final int mods = f.getModifiers();
        if (!Modifier.isPublic(mods) || Modifier.isStatic(mods)
                || BAD_FIELDS.contains(name)) {
            return;
        }
        findOrCreateBinding(name).setField(f);
    }

    public String getMethodBindings() {
        final StringBuilder sb = new StringBuilder();
        for (final Binding b : bindings.values()) {
            if (!b.hasGlobal()) {
                sb.append(b.toString());
            }
        }
        return sb.toString();
    }

    public String getFieldBindings() {
        final StringBuilder sb = new StringBuilder();
        for (final Binding b : bindings.values()) {
            if (b.hasGlobal() && !b.getGlobalType().equals(int.class)) {
                sb.append(b.toString());
            }
        }
        return sb.toString();
    }

    public String getIntegerFieldBindings() {
        final StringBuilder sb = new StringBuilder();
        for (final Binding b : bindings.values()) {
            if (b.hasGlobal() && b.getGlobalType().equals(int.class)) {
                sb.append(b.toString());
            }
        }
        return sb.toString();
    }

    public static String getText(final Reader r) throws IOException {
        final BufferedReader reader = new BufferedReader(r);
        final StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    public static void main(final String[] args) throws Exception {
        final DriverGenerator gen = new DriverGenerator();

        final String template = getText(new FileReader(args[0]));
        final String withMethodBindings = template.replace("%METHOD_BINDINGS%",
                gen.getMethodBindings());
        final String withFieldBindings = withMethodBindings.replace(
                "%FIELD_BINDINGS%", gen.getFieldBindings());
        final String withIntegerFieldBindings = withFieldBindings.replace(
                "%INTEGER_FIELD_BINDINGS%", gen.getIntegerFieldBindings());

        final FileWriter out = new FileWriter(args[1]);
        out.write(withIntegerFieldBindings);
        out.close();
    }
}
