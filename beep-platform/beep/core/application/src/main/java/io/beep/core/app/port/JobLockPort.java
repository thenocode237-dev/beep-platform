package io.beep.core.app.port;
import java.util.function.Supplier;
public interface JobLockPort {String run(String name,Supplier<String> work);}
