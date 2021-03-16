import com.jchen.rpc.api.HelloObject;
import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.client.RpcClientProxy;

/**
 * 测试用消费者（客户端）
 *
 * @Auther: jchen
 * @Date: 2021/03/15/20:43
 */
public class TestClient {

    public static void main(String[] args) {
        RpcClientProxy proxy = new RpcClientProxy("127.0.0.1", 9000);
        HelloService helloService = proxy.getProxy(HelloService.class);
        HelloObject object = new HelloObject(12, "This is a message");
        String res = helloService.hello(object);
        System.out.println(res);
    }

}
