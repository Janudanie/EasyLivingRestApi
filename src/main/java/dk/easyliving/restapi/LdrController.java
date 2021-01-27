package dk.easyliving.restapi;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import dk.easyliving.dto.units.LdrSensor;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

@RestController
public class LdrController {

    private static Connection connection;
    private static Channel channel;

    public LdrController() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.1.240");

        connection = factory.newConnection();
        channel = connection.createChannel();
    }



    @RequestMapping("/ldr")
    public String getLdrs(){
        System.out.println("Getting all LDR'S");
        String result = sendMessage("EasyLiving","GetAllLdr","anything");

        return result;
    }


    @PostMapping("/ldr")
    public String AddLdrSensor(@RequestBody LdrSensor tempLdrSensor){
        //send the LdrSensor to the RabbitMQ
        String result = sendMessage("EasyLiving","AddLdrSensor",tempLdrSensor.toJson());
        return result;
    }



    public static String sendMessage(String exchange, String topic, String message){
        final String corrId = UUID.randomUUID().toString();

        try
        {
            String replyQueueName =  channel.queueDeclare().getQueue();

            AMQP.BasicProperties props = new AMQP.BasicProperties
                    .Builder()
                    .replyTo(replyQueueName)
                    .correlationId(corrId)
                    .build();

            channel.basicPublish(exchange, topic, props, message.getBytes("UTF-8"));


            final BlockingQueue<String> response = new ArrayBlockingQueue<>(1);

            //wait for response

            String ctag = channel.basicConsume(replyQueueName, true, (consumerTag, delivery) -> {
                if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                    response.offer(new String(delivery.getBody(), "UTF-8"));
                }
            }, consumerTag -> {
            });

            String result = response.take();
            channel.basicCancel(ctag);
            //return the response
            return result;

        }
        catch (Exception e){
            //System.out.println(e.getMessage());
            System.out.println("Error happened");

        }
        return "unknown error";

    }
}
