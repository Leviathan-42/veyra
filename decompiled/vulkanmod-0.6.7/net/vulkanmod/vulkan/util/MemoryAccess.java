package net.vulkanmod.vulkan.util;

import java.lang.reflect.Field;
import org.lwjgl.system.Pointer;
import sun.misc.Unsafe;

public class MemoryAccess {
   public static final Unsafe UNSAFE;
   public static final boolean BITS64 = Pointer.BITS64;

   public static boolean memGetBoolean(long ptr) {
      return UNSAFE.getByte(null, ptr) != 0;
   }

   public static byte memGetByte(long ptr) {
      return UNSAFE.getByte(null, ptr);
   }

   public static short memGetShort(long ptr) {
      return UNSAFE.getShort(null, ptr);
   }

   public static int memGetInt(long ptr) {
      return UNSAFE.getInt(null, ptr);
   }

   public static long memGetLong(long ptr) {
      return UNSAFE.getLong(null, ptr);
   }

   public static float memGetFloat(long ptr) {
      return UNSAFE.getFloat(null, ptr);
   }

   public static double memGetDouble(long ptr) {
      return UNSAFE.getDouble(null, ptr);
   }

   public static long memGetAddress(long ptr) {
      return BITS64 ? UNSAFE.getLong(null, ptr) : UNSAFE.getInt(null, ptr) & 4294967295L;
   }

   public static void memPutByte(long ptr, byte value) {
      UNSAFE.putByte(null, ptr, value);
   }

   public static void memPutShort(long ptr, short value) {
      UNSAFE.putShort(null, ptr, value);
   }

   public static void memPutInt(long ptr, int value) {
      UNSAFE.putInt(null, ptr, value);
   }

   public static void memPutLong(long ptr, long value) {
      UNSAFE.putLong(null, ptr, value);
   }

   public static void memPutFloat(long ptr, float value) {
      UNSAFE.putFloat(null, ptr, value);
   }

   public static void memPutDouble(long ptr, double value) {
      UNSAFE.putDouble(null, ptr, value);
   }

   static {
      try {
         Field f = Unsafe.class.getDeclaredField("theUnsafe");
         f.setAccessible(true);
         UNSAFE = (Unsafe)f.get(null);
      } catch (NoSuchFieldException | IllegalAccessException e) {
         throw new RuntimeException(e);
      }
   }
}
