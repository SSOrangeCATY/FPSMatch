package com.tacz.guns.compat.kubejs.events;

import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.script.ScriptTypeHolder;
import dev.latvian.mods.kubejs.script.ScriptTypePredicate;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public interface TimelessKubeJSEventRegister {
    default void init() {}

    Map<Class<? extends Event>, Consumer<Event>> getEventHandlers();

    ScriptTypePredicate getScriptType();

    default <E extends Event> EventHandler registerTimelessEvent(
            String id,
            Class<? extends GunKubeJSEvents.GunEventJS<E>> eventJSClass,
            Class<E> eventClass,
            Function<E, ? extends GunKubeJSEvents.GunEventJS<E>> eventJSFactory
    ) {
        return registerTimelessEvent(id, eventJSClass, eventClass, eventJSFactory, false);
    }

    default <E extends Event> EventHandler registerTimelessEvent(
            String id,
            Class<? extends GunKubeJSEvents.GunEventJS<E>> eventJSClass,
            Class<E> eventClass,
            Function<E, ? extends GunKubeJSEvents.GunEventJS<E>> eventJSFactory,
            boolean hasResult
    ) {
        TargetedEventHandler<Identifier> handler = registerEventJS(id, eventJSClass, hasResult);
        registerEventHandler(eventClass, (event) -> {
            GunKubeJSEvents.GunEventJS<E> eventJS = eventJSFactory.apply((E) event);
            Identifier target = eventJS.getEventSubId();
            if (target != null) {
                handler.post(eventJS, target);
            } else {
                handler.post(eventJS);
            }
        });
        return handler;
    }

    default  <E extends Event> EventHandler registerTimelessCommonEvent(
            String id,
            Class<? extends GunKubeJSEvents.GunEventJS<E>> eventJSClass,
            Class<E> eventClass,
            Function<E, ? extends GunKubeJSEvents.GunEventJS<E>> eventJSFactory
    ) {
        return registerTimelessCommonEvent(id, eventJSClass, eventClass, eventJSFactory, false);
    }

    default <E extends Event> EventHandler registerTimelessCommonEvent(
            String id,
            Class<? extends GunKubeJSEvents.GunEventJS<E>> eventJSClass,
            Class<E> eventClass,
            Function<E, ? extends GunKubeJSEvents.GunEventJS<E>> eventJSFactory,
            boolean hasResult
    ) {
        TargetedEventHandler<Identifier> handler = registerEventJS(id, eventJSClass, hasResult);
        registerEventHandler(eventClass, (event) -> {
            GunKubeJSEvents.GunEventJS<E> eventJS = eventJSFactory.apply((E) event);
            ScriptTypeHolder holder = eventJS.getTypeHolder();
            if (holder != null) {
                Identifier target = eventJS.getEventSubId();
                if (target != null) {
                    handler.post(holder, target, eventJS);
                } else {
                    handler.post(holder, eventJS);
                }
            } else {
                throw new IllegalArgumentException("You must specify which script type to post event to");
            }
        });
        return handler;
    }

    default <E extends Event> TargetedEventHandler<Identifier> registerEventJS(String id, Class<? extends GunKubeJSEvents.GunEventJS<E>> eventJSClass, boolean hasResult) {
        TargetedEventHandler<Identifier> handler = GunKubeJSEvents.GROUP.add(id, getScriptType(), () -> eventJSClass)
                .supportsTarget(EventTargetType.ID);
        return hasResult ? handler.hasResult() : handler;
    }

    <E extends Event> void registerEventHandler(Class<E> eventClass, Consumer<Event> eventPoster);

    default boolean postKubeJSEvent(Event event) {
        Consumer<Event> eventHandler = getEventHandlers().get(event.getClass());
        if (eventHandler != null) {
            eventHandler.accept(event);
            return false;
        } else {
            return true;
        }
    }
}
