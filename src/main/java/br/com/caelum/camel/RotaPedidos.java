package br.com.caelum.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		// no meu contexto , adiciono uma nova rota que será definida no configue logo
		// abaixo
		
		RouteBuilder rota1 = new RouteBuilder() {
			
				@Override
				public void configure() throws Exception {
					
					from("file:pedidos?delay=5s&noop=true").
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

				}
			
		};

		RouteBuilder rota2 = new RouteBuilder() {
			
			@Override
			public void configure() throws Exception {
				
				from("timer://negociacoes?fixedRate=true&delay=1s&period=360s").
					to("http4://argentumws-spring.herokuapp.com/negociacoes").
					split().xpath("/list/negociacao").
					convertBodyTo(String.class).
					log("${body}").
					setHeader(Exchange.FILE_NAME, simple("negociacao-${header.CamelSplitIndex}.xml")).
				to("file:saida");

			}
		
	};

		context.addRoutes(rota2);

	context.start();
	Thread.sleep(5000);
	context.stop();

}}
