package com.motoapp.security;

// =============================================================================
// IMPORTS
// =============================================================================
//
// io.jsonwebtoken (JJWT) → biblioteca que adicionamos no pom.xml
// Cuida de toda a complexidade de criar e validar tokens JWT:
//   - Assinar com chave secreta (HMAC-SHA256)
//   - Serializar/deserializar o JSON do token
//   - Verificar expiração
//   - Verificar assinatura
// =============================================================================
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// =============================================================================
// @Service — camada de serviço
// =============================================================================
//
// @Service é uma especialização de @Component.
// Semanticamente indica que esta classe contém lógica de negócio/infraestrutura.
// O Spring a detecta no ComponentScan e a registra como bean singleton —
// uma única instância compartilhada em toda a aplicação.
//
// Por que singleton funciona aqui?
// JwtService não tem estado mutável (nenhum campo que muda entre chamadas).
// Todos os dados necessários vêm como parâmetros dos métodos.
// Singleton + sem estado = thread-safe por natureza.
// =============================================================================
@Service
public class JwtService {

    // =========================================================================
    // @Value — Injeção de propriedades do application.yml
    // =========================================================================
    //
    // @Value("${app.jwt.secret}") → lê o valor de app.jwt.secret do yml
    //
    // O Spring resolve esta anotação em tempo de inicialização:
    // 1. Lê o application.yml
    // 2. Encontra app.jwt.secret
    // 3. Injeta o valor neste campo
    //
    // Vantagem sobre hardcode: mudamos a chave no yml sem tocar no código.
    // Em produção: ${JWT_SECRET} leria de variável de ambiente.
    //
    // Por que não @Autowired aqui?
    // @Autowired é para injetar beans (objetos gerenciados pelo Spring).
    // @Value é para injetar valores simples (String, int, boolean...).
    // =========================================================================
    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration; // 86400000 = 24 horas em milissegundos

    // =========================================================================
    // ESTRUTURA DE UM TOKEN JWT
    // =========================================================================
    //
    // Um JWT tem 3 partes separadas por ponto:
    //
    // eyJhbGciOiJIUzI1NiJ9           ← HEADER (algoritmo + tipo)
    // .
    // eyJzdWIiOiJqb2FvQGVtYWlsLmNvbSIs... ← PAYLOAD (claims/dados)
    // .
    // SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c ← SIGNATURE (assinatura)
    //
    // HEADER: {"alg": "HS256", "typ": "JWT"}
    //
    // PAYLOAD (Claims): dados que carregamos no token
    //   sub  (subject)    = identificador do usuário (email no nosso caso)
    //   iat  (issued at)  = quando foi criado (timestamp)
    //   exp  (expiration) = quando expira (timestamp)
    //   + claims customizados que quisermos adicionar
    //
    // SIGNATURE: HMAC-SHA256(base64(header) + "." + base64(payload), secretKey)
    //   Garante que o token não foi alterado.
    //   Sem a chave secreta, é impossível criar uma assinatura válida.
    //
    // IMPORTANTE: O payload é apenas Base64 — NÃO é criptografado!
    // Qualquer um pode decodificar e ler. NUNCA coloque senha ou dado
    // sensível no payload. A segurança vem da ASSINATURA, não do sigilo.
    // =========================================================================

    // =========================================================================
    // GERAÇÃO DO TOKEN
    // =========================================================================

    // Gera um token JWT para o usuário autenticado.
    // UserDetails é a interface do Spring Security — compatível com nossa Entity User.
    public String generateToken(UserDetails userDetails) {
        // Sem claims extras — token simples com apenas o subject
        return generateToken(new HashMap<>(), userDetails);
    }

    // Sobrecarga que aceita claims adicionais.
    // Exemplo de uso: adicionar o role do usuário no token para
    // o frontend exibir menus corretos sem fazer outra requisição.
    //
    // Map<String, Object> extraClaims → pares chave-valor adicionais
    // Exemplo: {"role": "DRIVER", "planId": 2, "subscriptionStatus": "ACTIVE"}
    public String generateToken(Map<String, Object> extraClaims,
                                UserDetails userDetails) {
        return Jwts.builder()
                // Claims customizados (role, planId etc.) — adicionados primeiro
                .claims(extraClaims)

                // subject = identificador único do usuário no token
                // Usamos o email (getUsername() retorna email na nossa Entity)
                .subject(userDetails.getUsername())

                // iat = issued at = data/hora de criação do token
                .issuedAt(new Date(System.currentTimeMillis()))

                // exp = expiration = data/hora de expiração
                // System.currentTimeMillis() = agora em ms
                // + jwtExpiration = 86400000ms = 24 horas
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))

                // Assina o token com nossa chave secreta usando HMAC-SHA256
                // signWith() recebe a SecretKey gerada pelo método getSigningKey()
                .signWith(getSigningKey())

                // Serializa tudo em uma String compacta: header.payload.signature
                .compact();
    }

    // =========================================================================
    // VALIDAÇÃO DO TOKEN
    // =========================================================================

    // Verifica se o token é válido para este usuário.
    // Dois critérios devem ser verdadeiros:
    // 1. O email no token bate com o username do UserDetails
    // 2. O token não expirou
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // Verifica se o token já passou da data de expiração
    private boolean isTokenExpired(String token) {
        // extractExpiration retorna a data de expiração do token
        // before(new Date()) → a expiração está antes de agora? = expirou?
        return extractExpiration(token).before(new Date());
    }

    // =========================================================================
    // EXTRAÇÃO DE DADOS DO TOKEN (Claims)
    // =========================================================================

    // Extrai o email (subject) do token — usado para identificar o usuário
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Extrai a data de expiração do token
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Método genérico para extrair qualquer claim do token.
    //
    // Function<Claims, T> claimsResolver → função que recebe os Claims
    // e retorna o valor desejado. Permite reutilizar a lógica de parsing
    // para extrair qualquer campo sem duplicar código.
    //
    // Exemplos de uso:
    //   extractClaim(token, Claims::getSubject)    → email
    //   extractClaim(token, Claims::getExpiration) → data de expiração
    //   extractClaim(token, c -> c.get("role"))    → claim customizado
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        // Aplica a função passada sobre os claims extraídos
        return claimsResolver.apply(claims);
    }

    // Faz o parsing completo do token e retorna todos os claims.
    //
    // Se o token for inválido (assinatura incorreta, expirado, malformado),
    // o JJWT lança uma exceção automaticamente.
    // O JwtAuthFilter vai capturar e retornar 401 Unauthorized.
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                // Fornece a chave para verificar a assinatura
                .verifyWith(getSigningKey())
                .build()
                // Faz o parsing e verifica assinatura + expiração
                .parseSignedClaims(token)
                // Retorna apenas o payload (os claims)
                .getPayload();
    }

    // =========================================================================
    // CHAVE DE ASSINATURA
    // =========================================================================
    //
    // Converte a String do application.yml em uma SecretKey criptográfica.
    //
    // Por que não usar a String diretamente?
    // As APIs modernas de criptografia exigem objetos SecretKey tipados,
    // não Strings — evita erros de encoding e garante que a chave tem
    // comprimento e formato adequados.
    //
    // Keys.hmacShaKeyFor() → cria uma chave HMAC-SHA compatível com o tamanho
    // da String fornecida. Para HS256, a chave precisa ter no mínimo 256 bits
    // (32 bytes). Nossa chave no yml tem bem mais que isso.
    //
    // getBytes(StandardCharsets.UTF_8) → encoding explícito para evitar
    // comportamento diferente entre sistemas operacionais (Windows vs Linux).
    // =========================================================================
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}