package net.schwarzbaer.java.tools.lineeditor;

public class Debug {
	static void Assert(boolean condition) {
		if (!condition) throw new IllegalStateException();
	}
}
