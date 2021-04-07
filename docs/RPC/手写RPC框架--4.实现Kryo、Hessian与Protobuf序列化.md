## Kryo序列化

上一节我们实现了一个通用的序列化框架，使得序列化方式具有了较高的扩展性，并且实现了一个基于 JSON 的序列化器。

但是，我们也提到过，这个基于 JSON 的序列化器有一个毛病，就是在某个类的属性反序列化时，如果属性声明为 Object 的，就会造成反序列化出错，通常会把 Object 属性直接反序列化成 String 类型，就需要其他参数辅助序列化。并且，JSON 序列化器是基于字符串（JSON 串）的，占用空间较大且速度较慢。

这一节我们就来实现一个基于 Kryo 的序列化器。那么，什么是 Kryo？

Kryo 是一个快速高效的 Java 对象序列化框架，主要特点是高性能、高效和易用。最重要的两个特点，一是基于字节的序列化，对空间利用率较高，在网络传输时可以减小体积；二是序列化时记录属性对象的类型信息，无需传入Class或Type类信息，这样在反序列化时就不会出现之前的问题了。



### 实现接口

首先添加 kryo 的依赖

```xml
<dependency>
    <groupId>com.esotericsoftware</groupId>
    <artifactId>kryo</artifactId>
    <version>4.0.2</version>
</dependency>
```

我们在上一节定义了一个通用的序列化接口：

这里我们可以把 Kryo 的编号设为 0，后续会作为默认的序列化器，在静态方法的 switch 中加一个 case 即可。

根据接口，我们的主要任务就是实现其中的主要两个方法，`serialize()` 和 `deserialize()` ，如下：

```java
public class KryoSerializer implements CommonSerializer {

    private static final Logger logger = LoggerFactory.getLogger(KryoSerializer.class);

    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.register(RpcResponse.class);
        kryo.register(RpcRequest.class);
        kryo.setReferences(true);//打开循环引用的支持，防止栈内存溢出
        kryo.setRegistrationRequired(false);//禁止类注册
        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = new Output(byteArrayOutputStream);
        try {
            Kryo kryo = kryoThreadLocal.get();
            kryo.writeObject(output, obj);
            kryoThreadLocal.remove();
            return output.toBytes();
        } catch (Exception e) {
            logger.error("序列化时有错误发生:", e);
            e.printStackTrace();
            throw new SerializeException("序列化时有错误发生");
        }
    }

    @Override
    public Object deserialize(byte[] bytes, Class<?> clazz) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        Input input = new Input(byteArrayInputStream);
        try {
            Kryo kryo = kryoThreadLocal.get();
            Object o = kryo.readObject(input, clazz);
            kryoThreadLocal.remove();
            return o;
        } catch (Exception e) {
            logger.error("反序列化时有错误发生:", e);
            throw new SerializeException("反序列化时有错误发生");
        }
    }

    @Override
    public int getCode() {
        return SerializerCode.valueOf("KRYO").getCode();
    }
}
```

kryo的对象本身不是线程安全的，所以我们需要使用Threadlocal来保障线程安全，一个线程一个 Kryo。这里我们对kryo的初始化进行解释：

- **`kryo.register(RpcResponse.class);`** kryo在序列化对象时，首先会序列化其类的全限定名，由于我们通常序列化的对象都是有限范围内的类的实例，这样重复序列化同样的类的全限定名是低效的。通过注册kryo可以将类的全限定名抽象为一个数字，即用一个数字代表全限定名，这样就要高效一些。`kryo.register(SomeClass.class);`，注册方法的完整签名为`public Registration register (Class type, Serializer serializer, int id)`，我们通常只需要使用其重载方法即可`public Registration register (Class type)`，serializer和id在kryo内部会指定。
- **`kryo.setRegistrationRequired(false);`** 不强制要求注册类。注册会给每一个class一个int类型的Id相关联，这显然比类名称高效，但同时**要求反序列化的时候的Id必须与序列化过程中一致**。这意味着注册的顺序非常重要。但是由于现实原因，同样的代码，同样的Class在不同的机器上注册编号不能保证一致，所以多机器部署时候反序列化可能会出现问题。所以kryo默认会禁止类注册
- **`kryo.setReferences(true);`** 对A对象序列化时，默认情况下kryo会在每个成员对象第一次序列化时写入一个数字，该数字逻辑上就代表了对该成员对象的引用，如果后续有引用指向该成员对象，则直接序列化之前存入的数字即可，而不需要再次序列化对象本身。这种默认策略对于成员存在互相引用的情况较有利，如果确认没有循环引用，可以关闭否则就会造成空间浪费（因为每序列化一个成员对象，都多序列化一个数字）。

在序列化时，先创建一个 Output 对象（Kryo 框架的概念），接着使用 writeObject 方法将对象写入 Output 中，最后调用 Output 对象的 toByte() 方法即可获得对象的字节数组。反序列化则是从 Input 对象中直接 readObject，这里只需要传入对象的类型，而不需要具体传入每一个属性的类型信息。

最后 getCode 方法中事实上是把序列化的编号写在一个枚举类 `SerializerCode` 里了：

```java
public enum SerializerCode {

    KRYO(0),
    JSON(1);

    private final int code;
}
```

### 替换序列化器并测试

我们只需要把 NettyServer 和 NettyClient 责任链中的 CommonEncoder 传入的参数改成 KryoSerializer 即可使用 Kryo 序列化。

```java
pipeline.addLast(new CommonEncoder(new KryoSerializer()));
```

最后运行之前的测试，测试结果与之前相同即没问题。





## Hessian序列化

前面我们实现了基于Kryo的序列化器，Keyo是基于字节的序列化，对空间利用率较高，在网络传输时可以减小体积；二是序列化时记录属性对象的类型信息，无需传入Class或Type类信息。那么我们现在再来实现一下Hessian（反）序列化器。

Hessian序列化是一种支持**动态类型、跨语言、基于对象传输**的网络协议，Java对象序列化的二进制流可以被其他语言（如，c++，python）。特性如下：

- 自描述序列化类型。不依赖外部描述文件或者接口定义，用一个字节表示常用的基础类型，极大缩短二进制流。
- 语言无关，支持脚本语言
- 协议简单，比Java原生序列化高效，相比hessian1，hessian2中增加了压缩编码，其序列化二进制流大小是Java序列化的50%，序列化耗时是Java序列化的30%，反序列化耗时是Java序列化的20%。因此在基于RPC的调用方式中性能更好。

Hessian会把复杂的对象所有属性存储在一个Map中进行序列化。所以在父类、子类中存在同名成员变量的情况下，hessian序列化时，先序列化子类，然后序列化父类。因此，反序列化结果会导致子类同名成员变量被父类的值覆盖。

换个思路，既然你继承了一个父类，当然希望复用的越多越好，所以，使用hessian序列化的时候，避免开这一点就行了。

### 实现接口

这里的实现与Kryo序列化器类似，只用继承CommonSerializer接口并且重写序列化和反序列化方法即可：

```java
public class HessianSerializer implements CommonSerializer {

    private static final Logger logger = LoggerFactory.getLogger(HessianSerializer.class);

    @Override
    public byte[] serialize(Object obj) {
        HessianOutput hessianOutput = null;
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            hessianOutput = new HessianOutput(byteArrayOutputStream);
            hessianOutput.writeObject(obj);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            logger.error("序列化时发生错误：", e);
            throw new SerializeException("序列化时有错误发生：");
        } finally {
            if (hessianOutput != null) {
                try {
                    hessianOutput.close();
                } catch (IOException e) {
                    logger.error("关闭流时有错误发生：", e);
                }
            }
        }
    }

    @Override
    public Object deserialize(byte[] bytes, Class<?> clazz) {
        HessianInput hessianInput = null;
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            hessianInput = new HessianInput(byteArrayInputStream);
            return hessianInput.readObject();
        }catch (IOException e) {
            logger.error("反序列化时有错误发生：" , e);
            throw new SerializeException("反序列化时有错误发生：");
        }finally {
            if (hessianInput != null) {
                hessianInput.close();
            }
        }
    }

    @Override
    public int getCode() {
        return SerializerCode.valueOf("HESSIAN").getCode();
    }
}
```

实现之后替换序列化器并且测试即可，这里不再赘述。

## Protobuf序列化

```java
public class ProtobufSerializer implements CommonSerializer {
    private LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
    private Map<Class<?>, Schema<?>> schemaCache = new ConcurrentHashMap<>();

    @Override
    public byte[] serialize(Object obj) {
        Class clazz = obj.getClass();
        Schema schema = getSchema(clazz);
        byte[] data;
        try {
            data = ProtostuffIOUtil.toByteArray(obj, schema, buffer);
        } finally {
            buffer.clear();
        }
        return data;
    }

    private Schema getSchema(Class clazz) {
        Schema schema = schemaCache.get(clazz);
        if (Objects.isNull(schema)) {
            // 这个schema通过RuntimeSchema进行懒创建并缓存
            // 所以可以一直调用RuntimeSchema.getSchema(),这个方法是线程安全的
            schema = RuntimeSchema.getSchema(clazz);
            if (Objects.nonNull(schema)) {
                schemaCache.put(clazz, schema);
            }
        }
        return schema;
    }

    @Override
    public Object deserialize(byte[] bytes, Class<?> clazz) {
        Schema schema = getSchema(clazz);
        Object obj = schema.newMessage();
        ProtostuffIOUtil.mergeFrom(bytes, obj, schema);
        return obj;
    }

    @Override
    public int getCode() {
        return SerializerCode.valueOf("PROTOBUF").getCode();
    }
}
```

