package br.com.caelum.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		// no meu contexto , adiciono uma nova rota que ser� definida no configue logo
		// abaixo
		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				// para primeiro exemplo vamos pegar os arquivos que est�o na pasta pedidos
				// nos m�todos From e To est�o os endpoints para o trafego dos arquivos
				// e transferi-los para a pasta saida
				// o par�metro noop=true n�o apaga os arquivos da pasta origem
				from("file:pedidos?delay=5s&noop=true").
				//para definirmos nossos ids de frma din�mica
				setProperty("pedidoId", xpath("/pedido/id/text()")).
				setProperty("clienteId", xpath("/pedido/id/pagamento/text()")).
					split().  //padr�o splitter
						xpath("/pedido/itens/item").
					filter().  //padr�o filter
						xpath("/item/formato[text()='EBOOK']").
					//log("${exchange.pattern}"). // essa express�o de linguagem mostra o Exchange Pattern utilizado, como � uma via unidirecional aparecer� InOnly
					//log("${id}").
					marshal().// indica que queremos transformar a mensagem de um formato para outro
						xmljson().
					log("${id} - ${body}").
					//setHeader("CamelFileName", simple("${file:name.noext}.json")).
					//setHeader("CamelFileName", simple("${id}.json")).
					//Em vez de usar a id da mensagem podemos usar o nome do arquivo como base e concatenar com o SplitIndex
					//isso ajudar� a identificarmos splits ocorridos em um mesmo arquivo, no caso o arquivo 3.
					//setHeader(Exchange.FILE_NAME, simple("${file:name.noext}-${header.CamelSplitIndex}.json")).
					setHeader(Exchange.HTTP_METHOD, HttpMethods.GET).
					setHeader(Exchange.HTTP_QUERY, constant("clienteId=${properties.clienteId}&pedidoId=${properties.pedidoId}")).
					
				//to("file:saida"); - no primeiro momento apenas transferimos entre pastas 
				to("http4://localhost:8080/webservices/ebook/item");//faz um post por padr�o quando h� um body na mensagem , mas n�o temos muito controle

			}
		});

		context.start();
		Thread.sleep(5000);
		context.stop();

	}
}
