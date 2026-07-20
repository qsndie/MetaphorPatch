package de.robv.android.xposed;
import android.content.res.AssetManager;
import android.content.res.Resources;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import org.lsposed.external.*;
import java.util.*;

public final class XposedHelpers 
{
	private XposedHelpers() 
	{

	}
	private static final ConcurrentHashMap<MemberCacheKey.Field, Optional<Field>> fieldCache = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<MemberCacheKey.Method, Optional<Method>> methodCache = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<MemberCacheKey.Constructor, Optional<Constructor<?>>> constructorCache = new ConcurrentHashMap<>();
	private static final WeakHashMap<Object, HashMap<String, Object>> additionalFields = new WeakHashMap<>();
	private static final HashMap<String, ThreadLocal<AtomicInteger>> sMethodDepth = new HashMap<>();

	private abstract static class MemberCacheKey 
	{
		private final int hash;
		protected MemberCacheKey(int hash) 
		{
			this.hash = hash;

		}
		@Override
		public abstract boolean equals(Object obj);
		@Override
		public final int hashCode() 
		{
			return hash;

		}
		static final class Constructor extends MemberCacheKey 
		{
			private final Class<?> clazz;
			private final Class<?>[] parameters;
			private final boolean isExact;
			public Constructor(Class<?> clazz, Class<?>[] parameters, boolean isExact) 
			{
				super(31 * Objects.hash(clazz, isExact) + Arrays.hashCode(parameters));
				this.clazz = clazz;
				this.parameters = parameters;
				this.isExact = isExact;

			}
			@Override
			public boolean equals(Object o) 
			{
				if (this == o) return true;
				if (!(o instanceof Constructor)) return false;
				Constructor that = (Constructor) o;
				return isExact == that.isExact && Objects.equals(clazz, that.clazz) && Arrays.equals(parameters, that.parameters);

			}
			@Override
			public String toString() 
			{
				String str = clazz.getName() + getParametersString(parameters);
				if (isExact) 
				{
					return str + "#exact";

				} else 
				{
					return str;

				}

			}

		}
		static final class Field extends MemberCacheKey 
		{
			private final Class<?> clazz;
			private final String name;
			public Field(Class<?> clazz, String name) 
			{
				super(Objects.hash(clazz, name));
				this.clazz = clazz;
				this.name = name;

			}
			@Override
			public boolean equals(Object o) 
			{
				if (this == o) return true;
				if (!(o instanceof Field)) return false;
				Field field = (Field) o;
				return Objects.equals(clazz, field.clazz) && Objects.equals(name, field.name);

			}
			@Override
			public String toString() 
			{
				return clazz.getName() + "#" + name;

			}

		}
		static final class Method extends MemberCacheKey 
		{
			private final Class<?> clazz;
			private final String name;
			private final Class<?>[] parameters;
			private final boolean isExact;
			public Method(Class<?> clazz, String name, Class<?>[] parameters, boolean isExact) 
			{
				super(31 * Objects.hash(clazz, name, isExact) + Arrays.hashCode(parameters));
				this.clazz = clazz;
				this.name = name;
				this.parameters = parameters;
				this.isExact = isExact;

			}
			@Override
			public boolean equals(Object o) 
			{
				if (this == o) return true;
				if (!(o instanceof Method)) return false;
				Method method = (Method) o;
				return isExact == method.isExact && Objects.equals(clazz, method.clazz) && Objects.equals(name, method.name) && Arrays.equals(parameters, method.parameters);

			}
			@Override
			public String toString() 
			{
				String str = clazz.getName() + '#' + name + getParametersString(parameters);
				if (isExact) 
				{
					return str + "#exact";

				} else 
				{
					return str;

				}

			}

		}

	}

	public static Class<?> findClass(String className, ClassLoader classLoader) 
	{
		if (classLoader == null)   classLoader = XposedBridge.BOOTCLASSLOADER;
		try 
		{
			return lang3GetClass(classLoader, className, false);

		}
		catch (ClassNotFoundException e) 
		{
			throw new ClassNotFoundError(e);

		}

	}

	public static Class<?> findClassIfExists(String className, ClassLoader classLoader) 
	{
		try 
		{
			return findClass(className, classLoader);

		}
		catch (ClassNotFoundError e) 
		{
			return null;

		}

	}

	public static Field findField(Class<?> clazz, String fieldName) 
	{
		final MemberCacheKey.Field key = new MemberCacheKey.Field(clazz, fieldName);
		return fieldCache.computeIfAbsent(key, new Function<MemberCacheKey.Field, Optional<Field>>(){

				@Override
				public Optional<Field> apply(de.robv.android.xposed.XposedHelpers.MemberCacheKey.Field k)
				{
					try 
					{
						Field newField = findFieldRecursiveImpl(k.clazz, k.name);
						newField.setAccessible(true);
						return Optional.of(newField);

					}
					catch (NoSuchFieldException e) 
					{
						return Optional.empty();

					}
				}
			}).orElseThrow(new Supplier<Error>() {

				@Override
				public Error get()
				{
					return new NoSuchFieldError(key.toString());
				}
			});
	}

	public static Field findFieldIfExists(Class<?> clazz, String fieldName) 
	{
		try 
		{
			return findField(clazz, fieldName);

		}
		catch (NoSuchFieldError e) 
		{
			return null;

		}

	}
	private static Field findFieldRecursiveImpl(Class<?> clazz, String fieldName) throws NoSuchFieldException 
	{
		try 
		{
			return clazz.getDeclaredField(fieldName);

		}
		catch (NoSuchFieldException e) 
		{
			while (true) 
			{
				clazz = clazz.getSuperclass();
				if (clazz == null || clazz.equals(Object.class))  break;
				try 
				{
					return clazz.getDeclaredField(fieldName);

				}
				catch (NoSuchFieldException ignored) 
				{

				}

			}
			throw e;

		}

	}

	public static Field findFirstFieldByExactType(Class<?> clazz, Class<?> type) 
	{
		Class<?> clz = clazz;
		do 
		{
			for (Field field : clz.getDeclaredFields()) 
			{
				if (field.getType() == type) 
				{
					field.setAccessible(true);
					return field;

				}

			}

		}
		while ((clz = clz.getSuperclass()) != null);
		throw new NoSuchFieldError("Field of type " + type.getName() + " in class " + clazz.getName());

	}

	public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) 
	{
		if (parameterTypesAndCallback.length == 0 || !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook))   throw new IllegalArgumentException("no callback defined");
		XC_MethodHook callback = (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
		Method m = findMethodExact(clazz, methodName, getParameterClasses(clazz.getClassLoader(), parameterTypesAndCallback));
		return XposedBridge.hookMethod(m, callback);

	}

	public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) 
	{
		return findAndHookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);

	}

	public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) 
	{
		return findMethodExact(clazz, methodName, getParameterClasses(clazz.getClassLoader(), parameterTypes));

	}

	public static Method findMethodExactIfExists(Class<?> clazz, String methodName, Object... parameterTypes) 
	{
		try 
		{
			return findMethodExact(clazz, methodName, parameterTypes);

		}
		catch (ClassNotFoundError | NoSuchMethodError e) 
		{
			return null;

		}

	}

	public static Method findMethodExact(String className, ClassLoader classLoader, String methodName, Object... parameterTypes) 
	{
		return findMethodExact(findClass(className, classLoader), methodName, getParameterClasses(classLoader, parameterTypes));

	}

	public static Method findMethodExactIfExists(String className, ClassLoader classLoader, String methodName, Object... parameterTypes) 
	{
		try 
		{
			return findMethodExact(className, classLoader, methodName, parameterTypes);

		}
		catch (ClassNotFoundError | NoSuchMethodError e) 
		{
			return null;

		}

	}

	public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) 
	{
		final MemberCacheKey.Method key = new MemberCacheKey.Method(clazz, methodName, parameterTypes, true);
		return methodCache.computeIfAbsent(key, new Function<MemberCacheKey.Method, Optional<Method>>() {

				@Override
				public Optional<Method> apply(de.robv.android.xposed.XposedHelpers.MemberCacheKey.Method k)
				{
					try 
					{
						Method method = k.clazz.getDeclaredMethod(k.name, k.parameters);
						method.setAccessible(true);
						return Optional.of(method);

					}
					catch (NoSuchMethodException e) 
					{
						return Optional.empty();

					}
				}
			}).orElseThrow(new Supplier<Error>() {

				@Override
				public Error get()
				{
					return new NoSuchMethodError(key.toString());
				}
			});

	}

	public static Method[] findMethodsByExactParameters(Class<?> clazz, Class<?> returnType, Class<?>... parameterTypes) 
	{
		List<Method> result = new LinkedList<>();
		for (Method method : clazz.getDeclaredMethods()) 
		{
			if (returnType != null && returnType != method.getReturnType()) continue;
			Class<?>[] methodParameterTypes = method.getParameterTypes();
			if (parameterTypes.length != methodParameterTypes.length) continue;
			boolean match = true;
			for (int i = 0;
				 i < parameterTypes.length;
			i++) 
			{
				if (parameterTypes[i] != methodParameterTypes[i]) 
				{
					match = false;
					break;

				}

			}
			if (!match) continue;
			method.setAccessible(true);
			result.add(method);

		}
		return result.toArray(new Method[result.size()]);

	}
	static int compareConstructorFit(Constructor<?> constructor, Constructor<?> constructor2, Class<?>[] clsArr) {
        return compareParameterTypes(Executable.of(constructor), Executable.of(constructor2), clsArr);
    }

    static int compareMethodFit(Method method, Method method2, Class<?>[] clsArr) {
        return compareParameterTypes(Executable.of(method), Executable.of(method2), clsArr);
    }
	private static int compareParameterTypes(Executable executable, Executable executable2, Class<?>[] clsArr) {
        return Float.compare(getTotalTransformationCost(clsArr, executable), getTotalTransformationCost(clsArr, executable2));
    }

    private static float getObjectTransformationCost(Class<?> cls, Class<?> cls2) {
        if (cls2.isPrimitive()) {
            return getPrimitivePromotionCost(cls, cls2);
        }
        float f = 0.0f;
        while (true) {
            if (cls != null && !cls2.equals(cls)) {
                if (cls2.isInterface() && isAssignable(cls, cls2)) {
                    f += 0.25f;
                    break;
                }
                f += 1.0f;
                cls = cls.getSuperclass();
            } else {
                break;
            }
        }
        return cls == null ? f + 1.5f : f;
    }
	private static final Class<?>[] ORDERED_PRIMITIVE_TYPES;
	static {
        Class<?>[] clsArr = new Class[7];
        clsArr[0] = Byte.TYPE;
        clsArr[1] = Short.TYPE;
        clsArr[2] = Character.TYPE;
        clsArr[3] = Integer.TYPE;
        clsArr[4] = Long.TYPE;
        clsArr[5] = Float.TYPE;
        clsArr[6] = Double.TYPE;
        ORDERED_PRIMITIVE_TYPES = clsArr;
    }
    private static float getPrimitivePromotionCost(Class<?> cls, Class<?> cls2) {
        float f;
        if (cls == null) {
            return 1.5f;
        }
        if (!cls.isPrimitive()) {
            cls = wrapperToPrimitive(cls);
            f = 0.1f;
        } else {
            f = 0.0f;
        }
        int i = 0;
        while (cls != cls2) {
            Class<?>[] clsArr = ORDERED_PRIMITIVE_TYPES;
            if (i >= clsArr.length) {
                break;
            }
            if (cls == clsArr[i]) {
                f += 0.1f;
                if (i < clsArr.length - 1) {
                    cls = clsArr[i + 1];
                }
            }
            i++;
        }
        return f;
    }

    private static float getTotalTransformationCost(Class<?>[] clsArr, Executable executable) {
        float objectTransformationCost;
        Class<?>[] parameterTypes = executable.getParameterTypes();
        boolean isVarArgs = executable.isVarArgs();
        int length = parameterTypes.length;
        if (isVarArgs) {
            length--;
        }
        long j = (long) length;
        if (((long) clsArr.length) < j) {
            return Float.MAX_VALUE;
        }
        boolean z = false;
        float f = 0.0f;
        for (int i = 0; ((long) i) < j; i++) {
            f += getObjectTransformationCost(clsArr[i], parameterTypes[i]);
        }
        if (!isVarArgs) {
            return f;
        }
        boolean z2 = clsArr.length < parameterTypes.length;
        if (clsArr.length == parameterTypes.length && clsArr[clsArr.length - 1] != null && clsArr[clsArr.length - 1].isArray()) {
            z = true;
        }
        Class<?> componentType = parameterTypes[parameterTypes.length - 1].getComponentType();
        if (z2) {
            objectTransformationCost = getObjectTransformationCost(componentType, Object.class);
        } else if (z) {
            objectTransformationCost = getObjectTransformationCost(clsArr[clsArr.length - 1].getComponentType(), componentType);
        } else {
            for (int length2 = parameterTypes.length - 1; length2 < clsArr.length; length2++) {
                f += getObjectTransformationCost(clsArr[length2], componentType) + 0.001f;
            }
            return f;
        }
        return f + objectTransformationCost + 0.001f;
    }

	final static class Executable {
		private final boolean isVarArgs;
		private final Class<?>[] parameterTypes;

		private Executable(Constructor<?> constructor) {
			this.parameterTypes = constructor.getParameterTypes();
			this.isVarArgs = constructor.isVarArgs();
		}

		private Executable(Method method) {
			this.parameterTypes = method.getParameterTypes();
			this.isVarArgs = method.isVarArgs();
		}

		/* access modifiers changed from: private */
		public static Executable of(Constructor<?> constructor) {
			return new Executable(constructor);
		}

		/* access modifiers changed from: private */
		public static Executable of(Method method) {
			return new Executable(method);
		}

		public Class<?>[] getParameterTypes() {
			return this.parameterTypes;
		}

		public boolean isVarArgs() {
			return this.isVarArgs;
		}
	}
	public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>... parameterTypes) 
	{
		try 
		{
			return findMethodExact(clazz, methodName, parameterTypes);

		}
		catch (NoSuchMethodError ignored) 
		{

		}
		final MemberCacheKey.Method key = new MemberCacheKey.Method(clazz, methodName, parameterTypes, false);
		return methodCache.computeIfAbsent(key, new Function<MemberCacheKey.Method, Optional<Method>>(){

				@Override
				public Optional<Method> apply(de.robv.android.xposed.XposedHelpers.MemberCacheKey.Method k)
				{
					Method bestMatch = null;
					Class<?> clz = k.clazz;
					boolean considerPrivateMethods = true;
					do 
					{
						for (Method method : clz.getDeclaredMethods()) 
						{
							if (!considerPrivateMethods && Modifier.isPrivate(method.getModifiers()))   continue;
							if (method.getName().equals(k.name) && isAssignable(k.parameters,    method.getParameterTypes(),    true)) 
							{
								if (bestMatch == null || compareMethodFit(method,  bestMatch,  k.parameters) < 0) 
								{
									bestMatch = method;

								}

							}

						}
						considerPrivateMethods = false;

					}
					while ((clz = clz.getSuperclass()) != null);
					if (bestMatch != null) 
					{
						bestMatch.setAccessible(true);
						return Optional.of(bestMatch);

					} else 
					{
						return Optional.empty();

					}

				}
			}).orElseThrow(new Supplier<Error>() {

				@Override
				public Error get()
				{
					return new NoSuchMethodError(key.toString());
				}
			});

	}

	public static Method findMethodBestMatch(Class<?> clazz, String methodName, Object... args) 
	{
		return findMethodBestMatch(clazz, methodName, getParameterTypes(args));

	}

	public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object[] args) 
	{
		Class<?>[] argsClasses = null;
		for (int i = 0;
			 i < parameterTypes.length;
		i++) 
		{
			if (parameterTypes[i] != null) continue;
			if (argsClasses == null) argsClasses = getParameterTypes(args);
			parameterTypes[i] = argsClasses[i];

		}
		return findMethodBestMatch(clazz, methodName, parameterTypes);

	}

	public static Class<?>[] getParameterTypes(Object... args) 
	{
		Class<?>[] clazzes = new Class<?>[args.length];
		for (int i = 0;
			 i < args.length;
		i++) 
		{
			clazzes[i] = (args[i] != null) ? args[i].getClass() : null;

		}
		return clazzes;

	}

	private static Class<?>[] getParameterClasses(ClassLoader classLoader, Object[] parameterTypesAndCallback) 
	{
		Class<?>[] parameterClasses = null;
		for (int i = parameterTypesAndCallback.length - 1;
			 i >= 0;
		i--) 
		{
			Object type = parameterTypesAndCallback[i];
			if (type == null) throw new ClassNotFoundError("parameter type must not be null", null);
			if (type instanceof XC_MethodHook) continue;
			if (parameterClasses == null) parameterClasses = new Class<?>[i + 1];
			if (type instanceof Class) parameterClasses[i] = (Class<?>) type;
			else if (type instanceof String) parameterClasses[i] = findClass((String) type, classLoader);
			else throw new ClassNotFoundError("parameter type must either be specified as Class or String", null);

		}
		if (parameterClasses == null)   parameterClasses = new Class<?>[0];
		return parameterClasses;

	}

	public static Class<?>[] getClassesAsArray(Class<?>... clazzes) 
	{
		return clazzes;

	}
	private static String getParametersString(Class<?>... clazzes) 
	{
		StringBuilder sb = new StringBuilder("(");
		boolean first = true;
		for (Class<?> clazz : clazzes) 
		{
			if (first) first = false;
			else sb.append(",");
			if (clazz != null) sb.append(clazz.getCanonicalName());
			else sb.append("null");

		}
		sb.append(")");
		return sb.toString();

	}

	public static Constructor<?> findConstructorExact(Class<?> clazz, Object... parameterTypes) 
	{
		return findConstructorExact(clazz, getParameterClasses(clazz.getClassLoader(), parameterTypes));

	}

	public static Constructor<?> findConstructorExactIfExists(Class<?> clazz, Object... parameterTypes) 
	{
		try 
		{
			return findConstructorExact(clazz, parameterTypes);

		}
		catch (ClassNotFoundError | NoSuchMethodError e) 
		{
			return null;

		}

	}

	public static Constructor<?> findConstructorExact(String className, ClassLoader classLoader, Object... parameterTypes) 
	{
		return findConstructorExact(findClass(className, classLoader), getParameterClasses(classLoader, parameterTypes));

	}

	public static Constructor<?> findConstructorExactIfExists(String className, ClassLoader classLoader, Object... parameterTypes) 
	{
		try 
		{
			return findConstructorExact(className, classLoader, parameterTypes);

		}
		catch (ClassNotFoundError | NoSuchMethodError e) 
		{
			return null;

		}

	}

	public static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>... parameterTypes) 
	{
		final MemberCacheKey.Constructor key = new MemberCacheKey.Constructor(clazz, parameterTypes, true);
		return constructorCache.computeIfAbsent(key, new Function<MemberCacheKey.Constructor, Optional<java.lang.reflect.Constructor<?>>>() {

				@Override
				public Optional<Constructor<?>> apply(de.robv.android.xposed.XposedHelpers.MemberCacheKey.Constructor k)
				{
					try 
					{
						Constructor<?> constructor = k.clazz.getDeclaredConstructor(k.parameters);
						constructor.setAccessible(true);
						return Optional.of(constructor);

					}
					catch (NoSuchMethodException e) 
					{
						return Optional.empty();

					}
				}
			}).orElseThrow(new Supplier<Error>() {

				@Override
				public Error get()
				{
					return new NoSuchMethodError(key.toString());
				}
			});

	}

	public static XC_MethodHook.Unhook findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) 
	{
		if (parameterTypesAndCallback.length == 0 || !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook))   throw new IllegalArgumentException("no callback defined");
		XC_MethodHook callback = (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
		Constructor<?> m = findConstructorExact(clazz, getParameterClasses(clazz.getClassLoader(), parameterTypesAndCallback));
		return XposedBridge.hookMethod(m, callback);

	}

	public static XC_MethodHook.Unhook findAndHookConstructor(String className, ClassLoader classLoader, Object... parameterTypesAndCallback) 
	{
		return findAndHookConstructor(findClass(className, classLoader), parameterTypesAndCallback);

	}

	public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Class<?>... parameterTypes) 
	{
		try 
		{
			return findConstructorExact(clazz, parameterTypes);

		}
		catch (NoSuchMethodError ignored) 
		{

		}
		final MemberCacheKey.Constructor key = new MemberCacheKey.Constructor(clazz, parameterTypes, false);
		return constructorCache.computeIfAbsent(key, new Function<MemberCacheKey.Constructor, Optional<Constructor<?>>>(){

				@Override
				public Optional<Constructor<?>> apply(de.robv.android.xposed.XposedHelpers.MemberCacheKey.Constructor k)
				{
					Constructor<?> bestMatch = null;
					Constructor<?>[] constructors = k.clazz.getDeclaredConstructors();
					for (Constructor<?> constructor : constructors) 
					{
						if (isAssignable(k.parameters,   constructor.getParameterTypes(),   true)) 
						{
							if (bestMatch == null || compareConstructorFit(constructor,    bestMatch,    k.parameters) < 0) 
							{
								bestMatch = constructor;

							}

						}

					}
					if (bestMatch != null) 
					{
						bestMatch.setAccessible(true);
						return Optional.of(bestMatch);

					} else 
					{
						return Optional.empty();

					}
				}
			}).orElseThrow(new Supplier<Error>() {

				@Override
				public Error get()
				{
					return new NoSuchMethodError(key.toString());
				}
			});

	}

	public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Object... args) 
	{
		return findConstructorBestMatch(clazz, getParameterTypes(args));

	}

	public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Class<?>[] parameterTypes, Object[] args) 
	{
		Class<?>[] argsClasses = null;
		for (int i = 0;
			 i < parameterTypes.length;
		i++) 
		{
			if (parameterTypes[i] != null) continue;
			if (argsClasses == null) argsClasses = getParameterTypes(args);
			parameterTypes[i] = argsClasses[i];

		}
		return findConstructorBestMatch(clazz, parameterTypes);

	}

	public static final class ClassNotFoundError extends Error 
	{
		private static final long serialVersionUID = -1070936889459514628L;

		public ClassNotFoundError(Throwable cause) 
		{
			super(cause);

		}

		public ClassNotFoundError(String detailMessage, Throwable cause) 
		{
			super(detailMessage, cause);

		}

	}

	public static int getFirstParameterIndexByType(Member method, Class<?> type) 
	{
		Class<?>[] classes = (method instanceof Method) ? ((Method) method).getParameterTypes() : ((Constructor) method).getParameterTypes();
		for (int i = 0;
			 i < classes.length;
		i++) 
		{
			if (classes[i] == type) 
			{
				return i;

			}

		}
		throw new NoSuchFieldError("No parameter of type " + type + " found in " + method);

	}

	public static int getParameterIndexByType(Member method, Class<?> type) 
	{
		Class<?>[] classes = (method instanceof Method) ? ((Method) method).getParameterTypes() : ((Constructor) method).getParameterTypes();
		int idx = -1;
		for (int i = 0;
			 i < classes.length;
		i++) 
		{
			if (classes[i] == type) 
			{
				if (idx == -1) 
				{
					idx = i;

				} else 
				{
					throw new NoSuchFieldError("More than one parameter of type " + type + " found in " + method);

				}

			}

		}
		if (idx != -1) 
		{
			return idx;

		} else 
		{
			throw new NoSuchFieldError("No parameter of type " + type + " found in " + method);

		}

	}

	public static void setObjectField(Object obj, String fieldName, Object value) 
	{
		try 
		{
			findField(obj.getClass(), fieldName).set(obj, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setBooleanField(Object obj, String fieldName, boolean value) 
	{
		try 
		{
			findField(obj.getClass(), fieldName).setBoolean(obj, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setByteField(Object obj, String fieldName, byte value) 
	{
		try 
		{
			findField(obj.getClass(), fieldName).setByte(obj, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setCharField(Object obj, String fieldName, char value) 
	{
		try 
		{
			findField(obj.getClass(), fieldName).setChar(obj, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setDoubleField(Object obj, String fieldName, double value) 
	{
		try 
		{
			findField(obj.getClass(), fieldName).setDouble(obj, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setFloatField(Object obj, String fieldName, float value) 
	{
		try 
		{
			findField(obj.getClass(), fieldName).setFloat(obj, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setIntField(Object obj, String fieldName, int value) 
	{
		try 
		{
			findField(obj.getClass(), fieldName).setInt(obj, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setLongField(Object obj, String fieldName, long value) 
	{
		try 
		{
			findField(obj.getClass(), fieldName).setLong(obj, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}
	public static final char PACKAGE_SEPARATOR_CHAR = '.';

    /**
     * <p>The package separator String: {@code "&#x2e;"}.</p>
     */
    public static final String PACKAGE_SEPARATOR = String.valueOf(PACKAGE_SEPARATOR_CHAR);
	
	public static final char INNER_CLASS_SEPARATOR_CHAR = '$';

    /**
     * <p>The inner class separator String: {@code "$"}.</p>
     */
    public static final String INNER_CLASS_SEPARATOR = String.valueOf(INNER_CLASS_SEPARATOR_CHAR);
	private static final Map<Class<?>, Class<?>> primitiveWrapperMap = new HashMap<Class<?>, Class<?>>();
    static {
		primitiveWrapperMap.put(Boolean.TYPE, Boolean.class);
		primitiveWrapperMap.put(Byte.TYPE, Byte.class);
		primitiveWrapperMap.put(Character.TYPE, Character.class);
		primitiveWrapperMap.put(Short.TYPE, Short.class);
		primitiveWrapperMap.put(Integer.TYPE, Integer.class);
		primitiveWrapperMap.put(Long.TYPE, Long.class);
		primitiveWrapperMap.put(Double.TYPE, Double.class);
		primitiveWrapperMap.put(Float.TYPE, Float.class);
		primitiveWrapperMap.put(Void.TYPE, Void.TYPE);
    }

    /**
     * Maps wrapper {@code Class}es to their corresponding primitive types.
     */
    private static final Map<Class<?>, Class<?>> wrapperPrimitiveMap = new HashMap<Class<?>, Class<?>>();
    static {
        for (Class<?> primitiveClass : primitiveWrapperMap.keySet())
		{
            Class<?> wrapperClass = primitiveWrapperMap.get(primitiveClass);
            if (!primitiveClass.equals(wrapperClass))
			{
                wrapperPrimitiveMap.put(wrapperClass, primitiveClass);
            }
        }
    }

    /**
     * Maps a primitive class name to its corresponding abbreviation used in array class names.
     */
    private static final Map<String, String> abbreviationMap = new HashMap<String, String>();

    /**
     * Maps an abbreviation used in array class names to corresponding primitive class name.
     */
    private static final Map<String, String> reverseAbbreviationMap = new HashMap<String, String>();

    /**
     * Add primitive type abbreviation to maps of abbreviations.
     *
     * @param primitive Canonical name of primitive type
     * @param abbreviation Corresponding abbreviation of primitive type
     */
    private static void addAbbreviation(String primitive, String abbreviation)
	{
        abbreviationMap.put(primitive, abbreviation);
        reverseAbbreviationMap.put(abbreviation, primitive);
    }

    /**
     * Feed abbreviation maps
     */
    static {
        addAbbreviation("int", "I");
        addAbbreviation("boolean", "Z");
        addAbbreviation("float", "F");
        addAbbreviation("long", "J");
        addAbbreviation("short", "S");
        addAbbreviation("byte", "B");
        addAbbreviation("double", "D");
        addAbbreviation("char", "C");
    }

	public static Class<?>[] emptyClasses = new Class<?>[0];
	public static boolean isAssignable(Class<?>[] classArray, Class<?>... toClassArray)
	{
        return isAssignable(classArray, toClassArray, true);
    }
	public static boolean isAssignable(Class<?> cls, Class<?> toClass)
	{
        return isAssignable(cls, toClass, true);
    }
	public static boolean isAssignable(Class<?>[] classArray, Class<?>[] toClassArray, boolean autoboxing)
	{
        if (classArray == null)
		{
            classArray = emptyClasses;
        }
        if (toClassArray == null)
		{
            toClassArray = emptyClasses;
        }
		if (classArray.length != toClassArray.length)
		{
			return false;
		}
        for (int i = 0; i < classArray.length; i++)
		{
            if (isAssignable(classArray[i], toClassArray[i], autoboxing) == false)
			{
                return false;
            }
        }
        return true;
    }

	public static boolean isAssignable(Class<?> cls, Class<?> toClass, boolean autoboxing)
	{
        if (toClass == null)
		{
            return false;
        }
        // have to check for null, as isAssignableFrom doesn't
        if (cls == null)
		{
            return !toClass.isPrimitive();
        }
        //autoboxing:
        if (autoboxing)
		{
            if (cls.isPrimitive() && !toClass.isPrimitive())
			{
                cls = primitiveToWrapper(cls);
                if (cls == null)
				{
                    return false;
                }
            }
            if (toClass.isPrimitive() && !cls.isPrimitive())
			{
                cls = wrapperToPrimitive(cls);
                if (cls == null)
				{
                    return false;
                }
            }
        }
        if (cls.equals(toClass))
		{
            return true;
        }
        if (cls.isPrimitive())
		{
            if (toClass.isPrimitive() == false)
			{
                return false;
            }
            if (Integer.TYPE.equals(cls))
			{
                return Long.TYPE.equals(toClass)
                    || Float.TYPE.equals(toClass)
                    || Double.TYPE.equals(toClass);
            }
            if (Long.TYPE.equals(cls))
			{
                return Float.TYPE.equals(toClass)
                    || Double.TYPE.equals(toClass);
            }
            if (Boolean.TYPE.equals(cls))
			{
                return false;
            }
            if (Double.TYPE.equals(cls))
			{
                return false;
            }
            if (Float.TYPE.equals(cls))
			{
                return Double.TYPE.equals(toClass);
            }
            if (Character.TYPE.equals(cls))
			{
                return Integer.TYPE.equals(toClass)
                    || Long.TYPE.equals(toClass)
                    || Float.TYPE.equals(toClass)
                    || Double.TYPE.equals(toClass);
            }
            if (Short.TYPE.equals(cls))
			{
                return Integer.TYPE.equals(toClass)
                    || Long.TYPE.equals(toClass)
                    || Float.TYPE.equals(toClass)
                    || Double.TYPE.equals(toClass);
            }
            if (Byte.TYPE.equals(cls))
			{
                return Short.TYPE.equals(toClass)
                    || Integer.TYPE.equals(toClass)
                    || Long.TYPE.equals(toClass)
                    || Float.TYPE.equals(toClass)
                    || Double.TYPE.equals(toClass);
            }
            // should never get here
            return false;
        }
        return toClass.isAssignableFrom(cls);
    }

	public static Class<?> wrapperToPrimitive(Class<?> cls)
	{
        return wrapperPrimitiveMap.get(cls);
    }
	public static Class<?> primitiveToWrapper(Class<?> cls)
	{
        Class<?> convertedClass = cls;
        if (cls != null && cls.isPrimitive())
		{
            convertedClass = primitiveWrapperMap.get(cls);
        }
        return convertedClass;
    }
	public static String deleteWhitespace(String str) {
        if (str == null) {
            return str;
        }
		if (str.isEmpty())
		{
			return str;
		}
        int sz = str.length();
        char[] chs = new char[sz];
        int count = 0;
        for (int i = 0; i < sz; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                chs[count++] = str.charAt(i);
            }
        }
        if (count == sz) {
            return str;
        }
        return new String(chs, 0, count);
    }
	private static String toCanonicalName(String className) {
        className = deleteWhitespace(className);
        if (className == null) {
            throw new NullPointerException("className must not be null.");
        } else if (className.endsWith("[]")) {
            StringBuilder classNameBuffer = new StringBuilder();
            while (className.endsWith("[]")) {
                className = className.substring(0, className.length() - 2);
                classNameBuffer.append("[");
            }
            String abbreviation = abbreviationMap.get(className);
            if (abbreviation != null) {
                classNameBuffer.append(abbreviation);
            } else {
                classNameBuffer.append("L").append(className).append(";");
            }
            className = classNameBuffer.toString();
        }
        return className;
    }
	
	private static Class<?> lang3GetClass(
		ClassLoader classLoader, String className, boolean initialize) throws ClassNotFoundException
	{
        try
		{
            Class<?> clazz;
            if (abbreviationMap.containsKey(className))
			{
                String clsName = "[" + abbreviationMap.get(className);
                clazz = Class.forName(clsName, initialize, classLoader).getComponentType();
            } else
			{
                clazz = Class.forName(toCanonicalName(className), initialize, classLoader);
            }
            return clazz;
        }
		catch (ClassNotFoundException ex)
		{
            // allow path separators (.) as inner class name separators
            int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR_CHAR);

            if (lastDotIndex != -1)
			{
                try
				{
                    return lang3GetClass(classLoader, className.substring(0, lastDotIndex) +
										 INNER_CLASS_SEPARATOR_CHAR + className.substring(lastDotIndex + 1),
										 initialize);
                }
				catch (ClassNotFoundException ex2)
				{ // NOPMD
                    // ignore exception
                }
            }

            throw ex;
        }
    }


    /**
     * Returns the (initialized) class represented by {@code className}
     * using the current thread's context class loader. This implementation
     * supports the syntaxes "{@code java.util.Map.Entry[]}",
     * "{@code java.util.Map$Entry[]}", "{@code [Ljava.util.Map.Entry;}",
     * and "{@code [Ljava.util.Map$Entry;}".
     *
     * @param className  the class name
     * @return the class represented by {@code className} using the current thread's context class loader
     * @throws ClassNotFoundException if the class is not found
     */
  
	public static void setShortField(Object obj, String fieldName, short value) 
	{
		try 
		{
			findField(obj.getClass(), fieldName).setShort(obj, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static Object getObjectField(Object obj, String fieldName) 
	{
		try 
		{
			return findField(obj.getClass(), fieldName).get(obj);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static Object getSurroundingThis(Object obj) 
	{
		return getObjectField(obj, "this$0");

	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted") public static boolean getBooleanField(Object obj, String fieldName) 
	{
		try 
		{
			return findField(obj.getClass(), fieldName).getBoolean(obj);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static byte getByteField(Object obj, String fieldName) 
	{
		try 
		{
			return findField(obj.getClass(), fieldName).getByte(obj);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static char getCharField(Object obj, String fieldName) 
	{
		try 
		{
			return findField(obj.getClass(), fieldName).getChar(obj);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static double getDoubleField(Object obj, String fieldName) 
	{
		try 
		{
			return findField(obj.getClass(), fieldName).getDouble(obj);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static float getFloatField(Object obj, String fieldName) 
	{
		try 
		{
			return findField(obj.getClass(), fieldName).getFloat(obj);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static int getIntField(Object obj, String fieldName) 
	{
		try 
		{
			return findField(obj.getClass(), fieldName).getInt(obj);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static long getLongField(Object obj, String fieldName) 
	{
		try 
		{
			return findField(obj.getClass(), fieldName).getLong(obj);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static short getShortField(Object obj, String fieldName) 
	{
		try 
		{
			return findField(obj.getClass(), fieldName).getShort(obj);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) 
	{
		try 
		{
			findField(clazz, fieldName).set(null, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setStaticBooleanField(Class<?> clazz, String fieldName, boolean value) 
	{
		try 
		{
			findField(clazz, fieldName).setBoolean(null, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setStaticByteField(Class<?> clazz, String fieldName, byte value) 
	{
		try 
		{
			findField(clazz, fieldName).setByte(null, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setStaticCharField(Class<?> clazz, String fieldName, char value) 
	{
		try 
		{
			findField(clazz, fieldName).setChar(null, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setStaticDoubleField(Class<?> clazz, String fieldName, double value) 
	{
		try 
		{
			findField(clazz, fieldName).setDouble(null, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setStaticFloatField(Class<?> clazz, String fieldName, float value) 
	{
		try 
		{
			findField(clazz, fieldName).setFloat(null, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setStaticIntField(Class<?> clazz, String fieldName, int value) 
	{
		try 
		{
			findField(clazz, fieldName).setInt(null, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setStaticLongField(Class<?> clazz, String fieldName, long value) 
	{
		try 
		{
			findField(clazz, fieldName).setLong(null, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static void setStaticShortField(Class<?> clazz, String fieldName, short value) 
	{
		try 
		{
			findField(clazz, fieldName).setShort(null, value);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static Object getStaticObjectField(Class<?> clazz, String fieldName) 
	{
		try 
		{
			return findField(clazz, fieldName).get(null);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static boolean getStaticBooleanField(Class<?> clazz, String fieldName) 
	{
		try 
		{
			return findField(clazz, fieldName).getBoolean(null);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static byte getStaticByteField(Class<?> clazz, String fieldName) 
	{
		try 
		{
			return findField(clazz, fieldName).getByte(null);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static char getStaticCharField(Class<?> clazz, String fieldName) 
	{
		try 
		{
			return findField(clazz, fieldName).getChar(null);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static double getStaticDoubleField(Class<?> clazz, String fieldName) 
	{
		try 
		{
			return findField(clazz, fieldName).getDouble(null);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static float getStaticFloatField(Class<?> clazz, String fieldName) 
	{
		try 
		{
			return findField(clazz, fieldName).getFloat(null);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static int getStaticIntField(Class<?> clazz, String fieldName) 
	{
		try 
		{
			return findField(clazz, fieldName).getInt(null);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static long getStaticLongField(Class<?> clazz, String fieldName) 
	{
		try 
		{
			return findField(clazz, fieldName).getLong(null);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static short getStaticShortField(Class<?> clazz, String fieldName) 
	{
		try 
		{
			return findField(clazz, fieldName).getShort(null);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}

	}

	public static Object callMethod(Object obj, String methodName, Object... args) 
	{
		try 
		{
			return findMethodBestMatch(obj.getClass(), methodName, args).invoke(obj, args);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}
		catch (InvocationTargetException e) 
		{
			throw new InvocationTargetError(e.getCause());

		}

	}

	public static Object callMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object... args) 
	{
		try 
		{
			return findMethodBestMatch(obj.getClass(), methodName, parameterTypes, args).invoke(obj, args);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}
		catch (InvocationTargetException e) 
		{
			throw new InvocationTargetError(e.getCause());

		}

	}

	public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) 
	{
		try 
		{
			return findMethodBestMatch(clazz, methodName, args).invoke(null, args);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}
		catch (InvocationTargetException e) 
		{
			throw new InvocationTargetError(e.getCause());

		}

	}

	public static Object callStaticMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object... args) 
	{
		try 
		{
			return findMethodBestMatch(clazz, methodName, parameterTypes, args).invoke(null, args);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}
		catch (InvocationTargetException e) 
		{
			throw new InvocationTargetError(e.getCause());

		}

	}

	public static final class InvocationTargetError extends Error 
	{
		private static final long serialVersionUID = -1070936889459514628L;

		public InvocationTargetError(Throwable cause) 
		{
			super(cause);

		}

	}

	public static Object newInstance(Class<?> clazz, Object... args) 
	{
		try 
		{
			return findConstructorBestMatch(clazz, args).newInstance(args);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}
		catch (InvocationTargetException e) 
		{
			throw new InvocationTargetError(e.getCause());

		}
		catch (InstantiationException e) 
		{
			throw new InstantiationError(e.getMessage());

		}

	}

	public static Object newInstance(Class<?> clazz, Class<?>[] parameterTypes, Object... args) 
	{
		try 
		{
			return findConstructorBestMatch(clazz, parameterTypes, args).newInstance(args);

		}
		catch (IllegalAccessException e) 
		{
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());

		}
		catch (IllegalArgumentException e) 
		{
			throw e;

		}
		catch (InvocationTargetException e) 
		{
			throw new InvocationTargetError(e.getCause());

		}
		catch (InstantiationException e) 
		{
			throw new InstantiationError(e.getMessage());

		}

	}

	public static Object setAdditionalInstanceField(Object obj, String key, Object value) 
	{
		if (obj == null)   throw new NullPointerException("object must not be null");
		if (key == null)   throw new NullPointerException("key must not be null");
		HashMap<String, Object> objectFields;
		synchronized (additionalFields) 
		{
			objectFields = additionalFields.get(obj);
			if (objectFields == null) 
			{
				objectFields = new HashMap<>();
				additionalFields.put(obj, objectFields);

			}

		}
		synchronized (objectFields) 
		{
			return objectFields.put(key, value);

		}

	}

	public static Object getAdditionalInstanceField(Object obj, String key) 
	{
		if (obj == null)   throw new NullPointerException("object must not be null");
		if (key == null)   throw new NullPointerException("key must not be null");
		HashMap<String, Object> objectFields;
		synchronized (additionalFields) 
		{
			objectFields = additionalFields.get(obj);
			if (objectFields == null) return null;

		}
		synchronized (objectFields) 
		{
			return objectFields.get(key);

		}

	}

	public static Object removeAdditionalInstanceField(Object obj, String key) 
	{
		if (obj == null)   throw new NullPointerException("object must not be null");
		if (key == null)   throw new NullPointerException("key must not be null");
		HashMap<String, Object> objectFields;
		synchronized (additionalFields) 
		{
			objectFields = additionalFields.get(obj);
			if (objectFields == null) return null;

		}
		synchronized (objectFields) 
		{
			return objectFields.remove(key);

		}

	}

	public static Object setAdditionalStaticField(Object obj, String key, Object value) 
	{
		return setAdditionalInstanceField(obj.getClass(), key, value);

	}

	public static Object getAdditionalStaticField(Object obj, String key) 
	{
		return getAdditionalInstanceField(obj.getClass(), key);

	}

	public static Object removeAdditionalStaticField(Object obj, String key) 
	{
		return removeAdditionalInstanceField(obj.getClass(), key);

	}

	public static Object setAdditionalStaticField(Class<?> clazz, String key, Object value) 
	{
		return setAdditionalInstanceField(clazz, key, value);

	}

	public static Object getAdditionalStaticField(Class<?> clazz, String key) 
	{
		return getAdditionalInstanceField(clazz, key);

	}

	public static Object removeAdditionalStaticField(Class<?> clazz, String key) 
	{
		return removeAdditionalInstanceField(clazz, key);

	}

	public static byte[] assetAsByteArray(Resources res, String path) throws IOException 
	{
		return inputStreamToByteArray(res.getAssets().open(path));

	}

	static byte[] inputStreamToByteArray(InputStream is) throws IOException 
	{
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		byte[] temp = new byte[1024];
		int read;
		while ((read = is.read(temp)) > 0) 
		{
			buf.write(temp, 0, read);

		}
		is.close();
		return buf.toByteArray();

	}

	public static String getMD5Sum(String file) throws IOException 
	{
		try 
		{
			MessageDigest digest = MessageDigest.getInstance("MD5");
			InputStream is = new FileInputStream(file);
			byte[] buffer = new byte[8192];
			int read;
			while ((read = is.read(buffer)) > 0) 
			{
				digest.update(buffer, 0, read);

			}
			is.close();
			byte[] md5sum = digest.digest();
			BigInteger bigInt = new BigInteger(1, md5sum);
			return bigInt.toString(16);

		}
		catch (NoSuchAlgorithmException e) 
		{
			return "";

		}

	}

	public static int incrementMethodDepth(String method) 
	{
		return getMethodDepthCounter(method).get().incrementAndGet();

	}

	public static int decrementMethodDepth(String method) 
	{
		return getMethodDepthCounter(method).get().decrementAndGet();

	}

	public static int getMethodDepth(String method) 
	{
		return getMethodDepthCounter(method).get().get();

	}
	private static ThreadLocal<AtomicInteger> getMethodDepthCounter(String method) 
	{
		synchronized (sMethodDepth) 
		{
			ThreadLocal<AtomicInteger> counter = sMethodDepth.get(method);
			if (counter == null) 
			{
				counter = new ThreadLocal<AtomicInteger>() 
				{
					@Override
					protected AtomicInteger initialValue() 
					{
						return new AtomicInteger();

					}

				}
					;
				sMethodDepth.put(method, counter);

			}
			return counter;

		}

	}


}
