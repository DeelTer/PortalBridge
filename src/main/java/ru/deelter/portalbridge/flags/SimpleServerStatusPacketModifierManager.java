package ru.deelter.portalbridge.flags;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.network.protocol.status.ServerStatus;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

@Slf4j(topic = "PingPacketModifier")
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public final class SimpleServerStatusPacketModifierManager implements IServerStatusPacketModifierManager {

	private static final Unsafe UNSAFE;
	private static final Object CODEC_FIELD_BASE;
	private static final long CODEC_FIELD_OFFSET;

	private final List<IServerStatusPacketModifier> unsafeModifiers = new LinkedList<>();
	private final List<IServerStatusPacketModifier> modifiers = Collections.unmodifiableList(this.unsafeModifiers);

	private final Map<Plugin, List<IServerStatusPacketModifier>> modifiersByPlugin = new HashMap<>();
	private boolean enabled = false;

	private Codec<ServerStatus> originalCodec;

	@NonNull
	@Override
	public Collection<? extends IServerStatusPacketModifier> getModifiersByPlugin(@NonNull Plugin plugin) {
		return Collections.unmodifiableList(this.modifiersByPlugin.getOrDefault(plugin, Collections.emptyList()));
	}

	@Override
	public void registerModifier(@NonNull Plugin plugin, @NonNull IServerStatusPacketModifier modifier) {
		log.info("Registering ping packet modifier {} for plugin {}", modifier.getClass().getSimpleName(), plugin.getName());
		if (!this.isEnabled()) {
			throw new IllegalStateException("Cannot register packet handlers while manager is disabled");
		}
		this.unsafeModifiers.add(modifier);
		this.modifiersByPlugin.computeIfAbsent(plugin, __ -> new ArrayList<>()).add(modifier);
	}

	@Override
	public void unregisterModifier(@NonNull IServerStatusPacketModifier modifier) {
		log.info("Unregistering modifier {}", modifier.getClass().getSimpleName());
		this.unsafeModifiers.removeIf(other -> other == modifier);
		this.modifiersByPlugin.entrySet().removeIf(entry -> {
			entry.getValue().removeIf(other -> other == modifier);
			return entry.getValue().isEmpty();
		});
	}

	@Override
	@SuppressWarnings("unchecked")
	@SneakyThrows
	public void enable() {
		log.info("Enabling packet handler manager");
		if (this.isEnabled()) {
			throw new IllegalStateException("Manager is already enabled");
		}
		this.originalCodec = (Codec<ServerStatus>) UNSAFE.getObject(CODEC_FIELD_BASE, CODEC_FIELD_OFFSET);

		UNSAFE.putObject(CODEC_FIELD_BASE, CODEC_FIELD_OFFSET, new Codec<ServerStatus>() {
			@Override
			public <T> DataResult<T> encode(ServerStatus serverStatus, DynamicOps<T> ops, T prefix) {
				return originalCodec.encode(serverStatus, ops, prefix).map(encoded -> {
					for (IServerStatusPacketModifier modifier : modifiers) {
						encoded = modifier.modify(ops, encoded);
					}
					return encoded;
				});
			}

			@Override
			public <T> DataResult<Pair<ServerStatus, T>> decode(DynamicOps<T> ops, T input) {
				return originalCodec.decode(ops, input);
			}
		});
		this.enabled = true;
	}

	@Override
	@SneakyThrows
	public void disable() {
		if (!this.isEnabled()) {
			throw new IllegalStateException("Manager is not enabled");
		}
		UNSAFE.putObject(CODEC_FIELD_BASE, CODEC_FIELD_OFFSET, this.originalCodec);
		this.modifiersByPlugin.clear();
		this.unsafeModifiers.clear();
		this.originalCodec = null;
		this.enabled = false;
	}

	static {
		try {
			Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			Field moduleField = Class.class.getDeclaredField("module");
			Field internalUnsafeField = Unsafe.class.getDeclaredField("theInternalUnsafe");

			unsafeField.setAccessible(true);
			UNSAFE = (Unsafe) unsafeField.get(null);
			Object internalUnsafe = UNSAFE.getObject(UNSAFE.staticFieldBase(internalUnsafeField), UNSAFE.staticFieldOffset(internalUnsafeField));

			Module originalInternalUnsafeModule = internalUnsafe.getClass().getModule();
			UNSAFE.putObject(internalUnsafe.getClass(), UNSAFE.objectFieldOffset(moduleField), SimpleServerStatusPacketModifierManager.class.getModule());

			Method staticFieldOffsetMethod = Arrays.stream(internalUnsafe.getClass().getDeclaredMethods())
					.filter(m -> !Modifier.isStatic(m.getModifiers()))
					.filter(m -> m.getName().equals("staticFieldOffset"))
					.filter(m -> m.getParameterCount() == 1)
					.filter(m -> m.getParameterTypes()[0] == Field.class)
					.findFirst().orElseThrow();
			Method staticFieldBaseMethod = Arrays.stream(internalUnsafe.getClass().getDeclaredMethods())
					.filter(m -> !Modifier.isStatic(m.getModifiers()))
					.filter(m -> m.getName().equals("staticFieldBase"))
					.filter(m -> m.getParameterCount() == 1)
					.filter(m -> m.getParameterTypes()[0] == Field.class)
					.findFirst().orElseThrow();
			staticFieldOffsetMethod.setAccessible(true);
			staticFieldBaseMethod.setAccessible(true);

			UNSAFE.putObject(internalUnsafe.getClass(), UNSAFE.objectFieldOffset(moduleField), originalInternalUnsafeModule);

			Class.forName(ServerStatus.class.getName(), true, ServerStatus.class.getClassLoader());
			Field codecField = ServerStatus.class.getDeclaredField("CODEC");
			CODEC_FIELD_BASE = staticFieldBaseMethod.invoke(internalUnsafe, codecField);
			CODEC_FIELD_OFFSET = (long) staticFieldOffsetMethod.invoke(internalUnsafe, codecField);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
