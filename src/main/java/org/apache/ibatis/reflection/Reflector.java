/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
/**
  反射器，因反射比较消耗性能
 每个 Reflector 会缓存反射操作需要的类信息,比如构造方法，属性名，setting/getting 方法等等
*/
public class Reflector {
  // 对应的类
  private final Class<?> type;
  // 可读属性数组
  private final String[] readablePropertyNames;
  // 可写属性数组
  private final String[] writeablePropertyNames;
  // 属性对应的 setting 方法的映射
  private final Map<String, Invoker> setMethods = new HashMap<>();
  // 属性对应的 setting 方法的映射
  private final Map<String, Invoker> getMethods = new HashMap<>();
  // 属性对应的 setting 方法的方法参数类型的映射
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  // 属性对应的 getting 方法的返回值类型的映射
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  // 默认的构造方法
  private Constructor<?> defaultConstructor;
    // 不区分大小写的属性集合
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
      // 设置类型
    type = clazz;
    // 查找默认的构造器
    addDefaultConstructor(clazz);
    // 遍历 getting 方法
    addGetMethods(clazz);
    // 遍历 setting 方法
    addSetMethods(clazz);
    // 遍历成员变量
    addFields(clazz);
    // 初始化其它变量
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writeablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }
  /**
    查找默认无参构造方法
  */
  private void addDefaultConstructor(Class<?> clazz) {
    // 查找所有构造器
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    //查找无参的构造器
    for (Constructor<?> constructor : consts) {
      if (constructor.getParameterTypes().length == 0) {
          this.defaultConstructor = constructor;
      }
    }
  }
/**
 * 遍历 getter 方法，缓存起来
*/
  private void addGetMethods(Class<?> cls) {
      // 成员属性及其getter方法
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // 获得所有方法
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
        // 忽略有参数的方法
      if (method.getParameterTypes().length > 0) {
        continue;
      }
      String name = method.getName();
      // 以 get 和 is 方法名开头的说明是getting 方法
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is") && name.length() > 2)) {
          // 获取属性名字
        name = PropertyNamer.methodToProperty(name);
        // 添加到 conflictingGetters 中去
        addMethodConflict(conflictingGetters, name, method);
      }
    }
    // 解决冲突的 getting 方法
    resolveGetterConflicts(conflictingGetters);
  }
/**
    解决 getting 冲突的方法，最终，一个属性只保留一个对应的方法
*/
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
      // 遍历每个属性，查找最为匹配的方法
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      //
      String propName = entry.getKey();
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
            // 初始化胜利者
          winner = candidate;
          continue;
        }
        // 提取两者的返回值类型进行比较
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        // 假如类型相同
        if (candidateType.equals(winnerType)) {
            // 假如不是布尔类型就报错
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
                "Illegal overloaded getter method with ambiguous type for property "
                    + propName + " in class " + winner.getDeclaringClass()
                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
            // 假如是布尔类型而且以 is 开头，就选候选者
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
          // 以下两个判断 一个返回值的类型是不是另一个的子类，如果是的话，就选子类
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
        } else {
            // 类型不同的话报异常
          throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
                  + propName + " in class " + winner.getDeclaringClass()
                  + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      // 添加到 getMethods 和 getTypes 中
      addGetMethod(propName, winner);
    }
  }
/**

*/
  private void addGetMethod(String name, Method method) {
      // 判断是不是合理的属性名
    if (isValidPropertyName(name)) {
        // 属性名 和 getter 方法
      getMethods.put(name, new MethodInvoker(method));
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      // 属性名和返回值类型
      getTypes.put(name, typeToClass(returnType));
    }
  }
/**
 跟 get 的思路差不错
*/
  private void addSetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      String name = method.getName();
      /*
      * set方法的判定标准
      * 1 set 开头
      * 2 方法名大于 set 三个字
      * 3 参数只有一个
      * */
      if (name.startsWith("set") && name.length() > 3) {
        if (method.getParameterTypes().length == 1) {
          name = PropertyNamer.methodToProperty(name);
          // 添加到冲突列表
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    // 解决冲突
    resolveSetterConflicts(conflictingSetters);
  }
/**
    添加 getting 方法到list中
*/
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
      // 假如该map中没有key对应的值，就把值设置为一个list
    List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
    // 往这个list中添加getter方法
    list.add(method);
  }
/**
解决setter的方法的冲突
*/
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      // 获取该属性的 getter 方法
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      for (Method setter : setters) {
        Class<?> paramType = setter.getParameterTypes()[0];
        // 查找是否有个 getter 的返回值相对应的类型,有的话就是这个了
        if (paramType.equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (exception == null) {
          try {
              /*
                假如这个方法返回值不是get的返回值，就要更之前的匹配比较一下是不是互为子类关系，是的话选子类的
              * 从两个setter中试图选择一个更加匹配的
              * */
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
              // 没有更匹配的就算了
            match = null;
            exception = e;
          }
        }
      }
      if (match == null) {
        throw exception;
      } else {
          // 添加到 setMethods 和 setTypes中
        addSetMethod(propName, match);
      }
    }
  }
/**
    选择更匹配的 setter 方法
*/
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    // 看哪个的返回值是哪个的子类，谁是子类，就返回谁
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    // 无法互为父子关系，就报错
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
        + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
        + paramType2.getName() + "'.");
  }

  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }
/**
    将 Type 转换为 Class
*/
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    // 普通类型的话慧姐使用该类
    if (src instanceof Class) {
      result = (Class<?>) src;
      // 泛型类型，使用泛型
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
      // 泛型数组，获取具体类
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      // 泛型是一个类型的话，就使用这个类型的数组当做类型信息
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
          // 不是的话递归获取类型信息,然后使用这个类型的数组当做类型信息
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    // 都不符合的话，使用 Object 类
    if (result == null) {
      result = Object.class;
    }
    return result;
  }
/**
    该方法为 之前的 addGetMethods 和 addSetMethods 方法的补充，因为有些成员属性不存在对应的 getting 和 setting
*/
  private void  addFields(Class<?> clazz) {
      // 获取全部的成员变量
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
        // 假如 setting 中没有的话
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        // 假如不是 final 也不是 static的属性, 就放入到 set 相关的缓存中
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      // 不存在就放入 get 相关的缓存中
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    // 递归处理父类
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler Class.getMethods(),
   * because we want to look for private methods as well.
   *
   * @param cls The class
   * @return An array containing all methods in this class
   */
  /**
    获取所有的方法，包括私有的和父类的
  */
  private Method[] getClassMethods(Class<?> cls) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    // 当前解析的类
    Class<?> currentClass = cls;
    // 不解析Object
    while (currentClass != null && currentClass != Object.class) {
        // 记录当前类定义的方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
        // 记录接口中定义的方法
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      // 查找父类的
      currentClass = currentClass.getSuperclass();
    }
    // 转换为 Method 数组并返回
    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[methods.size()]);
  }
/**
    添加唯一的方法，因为会判断一下相同的方法签名是否已经有值了，如果有了，就不加了
 所以是唯一的
*/
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
        // 不需要桥接方法
      if (!currentMethod.isBridge()) {
          // 获取方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
          // 假如还没加过这个方法，就加进去
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }
    /**
        获取方法签名
        格式为: returnType#方法名:参数1，参数2，参数3
    */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    // 返回类型
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    // 方法的参数
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writeablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    // 缓存中有就有
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    // 返回驼峰命名的属性
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
