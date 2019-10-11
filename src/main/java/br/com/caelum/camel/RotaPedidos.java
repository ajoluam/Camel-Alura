package br.com.caelum.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
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
					split().
						xpath("/pedido/itens/item").
					filter().
						xpath("/item/formato[text()='EBOOK']").
					log("${exchange.pattern}"). // essa express�o de linguagem mostra o Exchange Pattern utilizado, como � uma via unidirecional aparecer� InOnly
					log("${id}").
					marshal().// indica que queremos transformar a mensagem de um formato para outro
						xmljson().
					log("${body}").
					setHeader("CamelFileName", simple("${file:name.noext}.json")).
				to("file:saida");

			}
		});

		context.start();
		Thread.sleep(20000);
		context.stop();

	}
}
