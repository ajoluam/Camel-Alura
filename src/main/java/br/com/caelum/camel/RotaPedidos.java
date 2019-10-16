package br.com.caelum.camel;

import java.text.SimpleDateFormat;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.dataformat.xstream.XStreamDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;



import com.thoughtworks.xstream.XStream;

public class RotaPedidos {

	private static com.mysql.cj.jdbc.MysqlConnectionPoolDataSource criaDataSource() {
		com.mysql.cj.jdbc.MysqlConnectionPoolDataSource mysqlDs = new com.mysql.cj.jdbc.MysqlConnectionPoolDataSource();
		mysqlDs.setDatabaseName("camel");
		mysqlDs.setServerName("localhost");
		mysqlDs.setPort(3306);
		mysqlDs.setUser("root");
		mysqlDs.setPassword("Mozao123");
		return mysqlDs;
	}
	
	public static void main(String[] args) throws Exception {

		//CamelContext context = new DefaultCamelContext();
		
		SimpleRegistry registro = new SimpleRegistry();
		registro.put("mysql", criaDataSource());
		CamelContext context = new DefaultCamelContext(registro);//construtor recebe registro
		context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://localhost:8161"));
		
		RouteBuilder rota1 = new RouteBuilder() {
			
				@Override
				public void configure() throws Exception {
					//Antes de mais nada precisamos definir o errohandler
					errorHandler(deadLetterChannel("file:erro").
							 maximumRedeliveries(3).
							 useOriginalMessage().
							 redeliveryDelay(2000).
							 onRedelivery(new Processor() {            
						            @Override
						            public void process(Exchange exchange) throws Exception {
						            	int counter = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
						                int max = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
						                System.out.println("Redelivery - " + counter + "/" + max );
						            }
						        })
							 ); 
					
					//from("file:pedidos?delay=5s&noop=true").
					from("activemq:queue:pedidos").
						routeId("rota-pedidos").
						to("validator:pedido.xsd"). // antes de iniciarmos nossas rotas precisamos validar o pedido, se o mesmo segue o padrão esperado
													// para isso utilizaremos um arquivo xsd onde constam os validadores de cada atributo
						multicast().
						//parallelProcessing(). - permite que cada sub-rota seja executada em uma thread diferente
						//timeout(5000. - podemos definir um timeout em milisegundos para o processamento
							to("direct:http"). // no lugar do direct (sincrono) podemo usar o seda(assincrono)
							to("direct:soap");
					
					
					from("direct:http").
						routeId("rota-http").
						setProperty("pedidoId", xpath("/pedido/id/text()")).
						setProperty("clienteId", xpath("/pedido/pagamento/email-titular/text()")).
						split(). // padrão
							xpath("/pedido/itens/item").
						filter(). // padrão filter
							xpath("/item/formato[text()='EBOOK']").
						// log("${exchange.pattern}"). // essa expressão de linguagem mostra o Exchange
						// Pattern utilizado, como é uma via unidirecional aparecerá InOnly
						// log("${id}").
						setProperty("ebookId", xpath("/item/livro/codigo/text()")).
						marshal().
							xmljson().
						log("${id} - ${body}").
						// setHeader("CamelFileName", simple("${file:name.noext}.json")).
						// setHeader("CamelFileName", simple("${id}.json")).
						// setHeader(Exchange.FILE_NAME,simple("${file:name.noext}-${header.CamelSplitIndex}.json")).
						setHeader(Exchange.HTTP_METHOD, HttpMethods.GET).
						setHeader(Exchange.HTTP_QUERY,simple("ebookId=${property.ebookId}&clienteId=${property.clienteId}&pedidoId=${property.pedidoId}")).
					// to("file:saida"); - no primeiro momento apenas transferimos entre pastas
					to("http4://localhost:8080/webservices/ebook/item");
					
					
					
					from("direct:soap").
						routeId("rota-soap").
						to("xslt:pedido-para-soap.xslt").
						log("Resultadro do template: ${body}").
						setHeader(Exchange.CONTENT_TYPE, constant("text/xml")). //indica que o conteudo pe XML
					to("http4://localhost:8080/webservices/financeiro");

				}
			
		};

		
		
				
		RouteBuilder rota2 = new RouteBuilder() {
			
			@Override
			public void configure() throws Exception {
				
				final XStream xstream = new XStream();
				xstream.alias("negociacao", Negociacao.class);
				
				from("timer://negociacoes?fixedRate=true&delay=1s&period=360s").
					to("http4://argentumws-spring.herokuapp.com/negociacoes").
					convertBodyTo(String.class).
						unmarshal(new XStreamDataFormat(xstream)).
					split(body()).
					process(new Processor() {
						
						@Override
						public void process(Exchange exchange) throws Exception {
							Negociacao negociacao = exchange.getIn().getBody(Negociacao.class);
				            exchange.setProperty("preco", negociacao.getPreco());
				            exchange.setProperty("quantidade", negociacao.getQuantidade());
				            String data = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(negociacao.getData().getTime());
				            exchange.setProperty("data", data);
							
						}
					}).
					setBody(simple("insert into negociacao (preco,quantidade,dataDoNegocio) values ('${property.preco}','${property.quantidade}','${property.data}')")).
					log("${body}"). //logando o comando sql
					delay(1000).
				to("jdbc:mysql");
					//end();
					//setHeader(Exchange.FILE_NAME, simple("negociacao-${header.CamelSplitIndex}.xml")).
				//to("file:saida");

			}
		
	};

		
	
	
	context.addRoutes(rota1);

	context.start();
	Thread.sleep(10000);
	context.stop();

}}
