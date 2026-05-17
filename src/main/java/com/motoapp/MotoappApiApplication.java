package com.motoapp;

/*
 * ═══════════════════════════════════════════════════════════════════════════
 * IMPORTS — O que estamos trazendo para este arquivo
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Em Java, diferente do PHP, precisamos importar explicitamente cada classe
 * que usamos. O compilador precisa saber exatamente de onde vem cada coisa.
 *
 * SpringApplication  → classe utilitária que inicializa todo o contexto Spring
 * SpringBootApplication → anotação "mágica" que liga tudo (explicada abaixo)
 */
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * ═══════════════════════════════════════════════════════════════════════════
 * @SpringBootApplication — A anotação mais importante do projeto
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Esta única anotação é, na verdade, um atalho para TRÊS anotações juntas:
 *
 * 1. @Configuration
 *    Diz ao Spring: "esta classe pode declarar beans (objetos gerenciados
 *    pelo Spring)". Pense em beans como objetos que o Spring cria e cuida
 *    para você — você não usa "new MinhaClasse()", o Spring faz isso.
 *
 * 2. @EnableAutoConfiguration
 *    A "mágica" do Spring Boot. Ele analisa as dependências no pom.xml
 *    e configura automaticamente o que encontrar. Por exemplo:
 *    - Encontrou spring-boot-starter-web? Configura o Tomcat automaticamente.
 *    - Encontrou spring-boot-starter-data-jpa? Configura o Hibernate.
 *    - Encontrou mysql-connector-j? Cria um DataSource para o MySQL.
 *    Sem o Spring Boot, teríamos que fazer tudo isso manualmente.
 *
 * 3. @ComponentScan
 *    Diz ao Spring para varrer TODOS os pacotes a partir de "com.motoapp"
 *    procurando classes anotadas com @Service, @Controller, @Repository etc.
 *    Quando encontra, registra automaticamente como beans gerenciados.
 *    É por isso que colocamos tudo dentro de "com.motoapp" — o scan parte
 *    do pacote desta classe e desce para todos os subpacotes.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * CONCEITO IMPORTANTE: Inversão de Controle (IoC) e Injeção de Dependência
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Sem Spring (modo "manual"):
 *   UserService service = new UserService(new UserRepository(new DataSource(...)));
 *
 * Com Spring (IoC):
 *   @Service                    // "Spring, gerencie esta classe"
 *   public class UserService {
 *       @Autowired              // "Spring, injete o que eu preciso aqui"
 *       private UserRepository repo;
 *   }
 *
 * O Spring cria os objetos na ordem certa e os injeta onde precisam ser.
 * Você declara o que precisa, o Spring resolve como fornecer.
 * Isso é Injeção de Dependência — um dos pilares do Spring.
 */
@SpringBootApplication
public class MotoAppApplication {

    /*
     * ═══════════════════════════════════════════════════════════════════════
     * main() — O ponto de entrada de qualquer programa Java
     * ═══════════════════════════════════════════════════════════════════════
     *
     * Todo programa Java começa pelo método main(). É a primeira coisa
     * que a JVM (Java Virtual Machine) procura ao executar o .jar.
     *
     * String[] args → argumentos de linha de comando
     * ex: java -jar motoapp.jar --server.port=9090
     *     args seria: ["--server.port=9090"]
     *
     * O Spring Boot aceita esses args para sobrescrever configurações
     * do application.yml em tempo de execução — útil em produção.
     */
    public static void main(String[] args) {

        /*
         * SpringApplication.run() faz tudo isso em sequência:
         *
         * 1. Cria o ApplicationContext — o "contêiner de beans" do Spring,
         *    que é basicamente um mapa gigante de {nome → objeto gerenciado}
         *
         * 2. Executa o @ComponentScan — varre os pacotes procurando
         *    @Service, @Controller, @Repository, @Component etc.
         *
         * 3. Executa o AutoConfiguration — configura Tomcat, JPA,
         *    Security etc. com base nas dependências encontradas
         *
         * 4. Injeta as dependências — resolve todas as anotações
         *    @Autowired e @Value em todas as classes encontradas
         *
         * 5. Inicia o servidor Tomcat embutido na porta definida
         *    em application.yml (padrão: 8080)
         *
         * 6. O app está pronto para receber requisições HTTP
         *
         * MotoAppApplication.class → diz ao Spring onde está o pacote
         * raiz para começar o ComponentScan (com.motoapp)
         */
        SpringApplication.run(MotoAppApplication.class, args);
    }
}