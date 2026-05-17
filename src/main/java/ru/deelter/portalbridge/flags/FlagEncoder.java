package ru.deelter.portalbridge.flags;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public class FlagEncoder {

	private static final char ZWSP = '\u200B'; // 1
	private static final char ZWNJ = '\u200C'; // 0
	private static final int FLAG_COUNT = ServerFlag.values().length;

	/**
	 * Кодирует набор флагов в невидимую строку длиной FLAG_COUNT символов.
	 *
	 * @param flags набор флагов (если флаг установлен – 1, иначе 0)
	 * @return строка из \u200B и \u200C
	 */
	public static @NotNull String encode(@NotNull Set<ServerFlag> flags) {
		int bits = 0;
		for (ServerFlag flag : flags) {
			bits |= (1 << flag.ordinal());
		}
		StringBuilder sb = new StringBuilder(FLAG_COUNT);
		for (int i = 0; i < FLAG_COUNT; i++) {
			sb.append(((bits >> i) & 1) == 1 ? ZWSP : ZWNJ);
		}
		return sb.toString();
	}

	/**
	 * Декодирует первые FLAG_COUNT символов MOTD в набор флагов.
	 * Если MOTD короче FLAG_COUNT, недостающие биты считаются 0.
	 */
	public static @NotNull Set<ServerFlag> decode(@NotNull String motd) {
		Set<ServerFlag> result = EnumSet.noneOf(ServerFlag.class);
		int length = Math.min(motd.length(), FLAG_COUNT);
		for (int i = 0; i < length; i++) {
			char c = motd.charAt(i);
			if (c == ZWSP) {
				result.add(ServerFlag.fromOrdinal(i));
			} // ZWNJ означает 0 – ничего не добавляем
		}
		return result;
	}

	/**
	 * Проверяет наличие конкретного флага в MOTD (без декодирования всего набора).
	 */
	public static boolean hasFlag(@NotNull String motd, @NotNull ServerFlag flag) {
		int ordinal = flag.ordinal();
		if (ordinal >= motd.length()) return false;
		return motd.charAt(ordinal) == ZWSP;
	}

	/**
	 * Удаляет все невидимые флаговые символы из начала MOTD, возвращая чистый MOTD.
	 */
	public static @NotNull String stripFlags(@NotNull String motd) {
		int i = 0;
		while (i < motd.length() && (motd.charAt(i) == ZWSP || motd.charAt(i) == ZWNJ)) {
			i++;
		}
		return motd.substring(i);
	}

	public enum ServerFlag {

		PLUGIN_INSTALLED,
		PROXY,
		ONLINE_MODE,
		AUTH,
		ANTI_CHEAT,
		WHITELIST,
		TRANSFERS,
		LOGS_IP;

		public static @Nullable ServerFlag fromOrdinal(int ordinal) {
			if (ordinal < 0 || ordinal >= values().length) return null;
			return values()[ordinal];
		}
	}
}