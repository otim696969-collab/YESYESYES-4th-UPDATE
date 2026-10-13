package com.selfmade;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.Function;

/** Every item this mod adds. */
public final class ModItems {
	public static final Item IDENTITY_MIRROR =
			register("identity_mirror", IdentityMirrorItem::new, new Item.Properties().stacksTo(1));

	/** The starting book. */
	public static final Item FIELD_NOTES = register("field_notes", FieldNotesItem::new, new Item.Properties().stacksTo(1));

	/** One fragment item per {@link Memory}, indexed by ordinal. */
	private static final Item[] FRAGMENTS = new Item[Memory.values().length];

	static {
		for (Memory memory : Memory.values()) {
			FRAGMENTS[memory.ordinal()] = register(memory.itemName, props -> new MemoryFragmentItem(props, memory),
					new Item.Properties().stacksTo(1));
		}
	}

	private ModItems() {
	}

	public static Item fragment(Memory memory) {
		return FRAGMENTS[memory.ordinal()];
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties settings) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(SelfMade.MOD_ID, name));
		T item = factory.apply(settings.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	/** Called once from the mod initializer; touching this class is what registers the items above. */
	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(tab -> {
					tab.accept(IDENTITY_MIRROR);
					tab.accept(FIELD_NOTES);
					for (Item fragment : FRAGMENTS) {
						tab.accept(fragment);
					}
				});
	}
}
