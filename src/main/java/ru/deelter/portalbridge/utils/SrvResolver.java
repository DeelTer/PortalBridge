package ru.deelter.portalbridge.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SrvResolver {

	public static @Nullable SrvRecord resolve(String host) {
		try {
			Hashtable<String, String> env = new Hashtable<>();
			env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
			InitialDirContext ctx = new InitialDirContext(env);

			String lookup = "_minecraft._tcp." + host;
			Attributes attrs = ctx.getAttributes(lookup, new String[]{"SRV"});
			if (attrs.get("SRV") == null) return null;

			String srv = attrs.get("SRV").get().toString();
			String[] parts = srv.split(" ");
			if (parts.length < 4) return null;
			int priority = Integer.parseInt(parts[0]);
			int weight = Integer.parseInt(parts[1]);
			int port = Integer.parseInt(parts[2]);
			String target = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
			return new SrvRecord(priority, weight, port, target);
		} catch (Exception e) {
			return null;
		}
	}

	public record SrvRecord(int priority, int weight, int port, String target) {
	}
}