/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is camel-spring-amqp.
 * 
 * The Initial Developer of the Original Code is Bluelock, LLC.
 * Copyright (c) 2007-2011 Bluelock, LLC. All Rights Reserved.
 */
package amqp.spring.camel.component;

import java.util.ArrayList;
import java.util.StringTokenizer;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.TopicExchange;

/**
 * RabbitMQ Consumer URIs are in the format of:<br/>
 * <code>spring-amqp:exchange:queue:routingKey?params=values</code><br/>
 * 
 * RabbitMQ Producer URIs are in the format of:<br/>
 * <code>spring-amqp:exchange:routingKey?params=values</code><br/>
 * 
 * Producers can also defer the routing key to the message header, in which case the URI could be:<br/>
 * <code>spring-amqp:exchange?params=values</code><br/>
 * And the ROUTING_KEY header could be set to the appropriate routing key.
 */
public class SpringAMQPEndpoint extends DefaultEndpoint {
    private static transient final Logger LOG = LoggerFactory.getLogger(SpringAMQPEndpoint.class);
    
    protected AmqpAdmin amqpAdministration;
    protected AmqpTemplate amqpTemplate;
            
    String exchangeName;
    String queueName;
    String routingKey;
    String exchangeType;
    boolean durable = false;
    boolean exclusive = false;
    boolean autodelete = true;
    boolean transactional = false;
    int concurrentConsumers = 1;
    
    //The second and third parameters to the URI can be interchangable based on the context.
    //Place them here until we determine if we're a consumer or producer.
    private String tempQueueOrKey;
    
    public SpringAMQPEndpoint(String remaining, AmqpTemplate template, AmqpAdmin admin) {
        LOG.info("Creating endpoint for {}", remaining);
        this.amqpAdministration = admin;
        this.amqpTemplate = template;
        
        ArrayList<String> tokens = new ArrayList<String>();
        StringTokenizer uriTokenizer = new StringTokenizer(remaining, ":");
        while(uriTokenizer.hasMoreTokens())
            tokens.add(uriTokenizer.nextToken());
        
        //Per spec expected default is empty string
        this.exchangeName = tokens.isEmpty() || tokens.get(0) == null ? "" : tokens.get(0); 
        //Consumers must specify exchange, queue and routing key in that order
        if(tokens.size() > 2) { 
            this.queueName = tokens.get(1);
            this.routingKey = tokens.get(2);
        //We have only 2 parameters. Is this a routing key or a queue? We don't know yet.
        } else if(tokens.size() == 2) {
            this.tempQueueOrKey = tokens.get(1);
        //We only have the exchange name - that's it. This must be a fanout producer.
        } else {
            this.exchangeType = "fanout";
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        if(this.exchangeName == null)
            throw new IllegalStateException("Cannot have null exchange name");
        
        //Aha! We're a producer, so the second argument was a routing key.
        if(this.tempQueueOrKey != null) {
            this.routingKey = this.tempQueueOrKey;
            this.tempQueueOrKey = null;
        }
        
        return new SpringAMQPProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if(this.exchangeName == null)
            throw new IllegalStateException("Cannot have null exchange name");

        //Aha! We're a consumer, so the second argument was a queue name. This is a fanout exchange.
        if(this.tempQueueOrKey != null) {
            this.queueName = this.tempQueueOrKey;
            this.tempQueueOrKey = null;
            if(this.exchangeType == null)
                this.exchangeType = "fanout";
        }
        
        if(this.queueName == null)
            throw new IllegalStateException("Cannot have null queue name for "+getEndpointUri());
        
        return new SpringAMQPConsumer(this, processor);
    }

    public AmqpAdmin getAmqpAdministration() {
        return amqpAdministration;
    }

    public void setAmqpAdministration(AmqpAdmin amqpAdministration) {
        this.amqpAdministration = amqpAdministration;
    }

    public AmqpTemplate getAmqpTemplate() {
        return amqpTemplate;
    }

    public void setAmqpTemplate(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public boolean isTransactional() {
        return transactional;
    }

    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public boolean isAutodelete() {
        return autodelete;
    }

    public void setAutodelete(boolean autodelete) {
        this.autodelete = autodelete;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public String getType() {
        return exchangeType;
    }

    public void setType(String exchangeType) {
        this.exchangeType = exchangeType;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    @Override
    public boolean isSingleton() {
        //TODO Technically is it true that there can always be multiple consumers, even for AMQP queues?
        return false;
    }

    @Override
    protected String createEndpointUri() {
        StringBuilder builder = new StringBuilder("spring-amqp:").append(this.exchangeName);
        if(this.queueName != null)
            builder.append(":").append(this.queueName);
        if(this.routingKey != null)
            builder.append(":").append(this.routingKey);
        builder.append("?").append("type=").append(this.exchangeType);
        builder.append("&autodelete=").append(this.autodelete);
        builder.append("&concurrentConsumers=").append(this.concurrentConsumers);
        builder.append("&durable=").append(this.durable);
        builder.append("&exclusive=").append(this.exclusive);
        builder.append("&transactional=").append(this.transactional);
        
        return builder.toString();        
    }
    
    org.springframework.amqp.core.Exchange createAMQPExchange() {
        if(this.exchangeType == null || "direct".equals(this.exchangeType)) {
            return new DirectExchange(this.exchangeName, this.durable, this.autodelete);
        } else if("fanout".equals(this.exchangeType)) {
            return new FanoutExchange(this.exchangeName, this.durable, this.autodelete);
        } else if("headers".equals(this.exchangeType)) {
            return new HeadersExchange(this.exchangeName, this.durable, this.autodelete);
        } else if("topic".equals(this.exchangeType)) {
            return new TopicExchange(this.exchangeName, this.durable, this.autodelete);
        } else {
            return new DirectExchange(this.exchangeName, this.durable, this.autodelete);
        }
    }
}
