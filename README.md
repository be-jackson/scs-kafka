### 스프링 클라우드 스트림 for Kafka

[스프링 클라우드 스트림](https://spring.io/projects/spring-cloud-stream) 그리고 [Apache Kafka](https://kafka.apache.org/) 조합으로 PUSH 발송 시스템을 만들어보자. PUSH 발송 시스템의 기본 구조는 아래 그림과 [관련 문서](https://www.notion.so/dunamu/PUSH-Conceptual-Architecture-347fb776efde45d8878af3a27c061350) 를 참고하자.

구현 범위
- 푸시 발송 요청을 REST API 인터페이스로 수신
- 푸시 발송 요청을 `발송요청 Topic`에 전송
- `발송요청 Topic`, `발송재시도 Topic` 에서 요청을 수신하고, PUSH 발송 처리
  - 발송 결과가 성공인 경우, 내역을 `발송성공 Topic`에 전송
  - 재시도 해야한다면, 내역을 `발송재시도 Topic`에 전송 (무한 재시도를 방지하기 위해 최대 재시도 3회)
  - 발송 결과가 실패인 경우, 내역을 `발송실패 Topic`에 전송
- `발송성공 Topic`, `발송실패 Topic` 에서 요청을 수신하고 로그를 남김

> 본 PoC 프로젝트에서 어드민, 고객접촉이력 API, 실제 PUSH 발송 로직, Database 저장 로직 구현은 제외함

![image](https://s3.us-west-2.amazonaws.com/secure.notion-static.com/bfc2e3b9-139c-4945-a10b-f13615a60105/%E1%84%89%E1%85%B3%E1%84%8F%E1%85%B3%E1%84%85%E1%85%B5%E1%86%AB%E1%84%89%E1%85%A3%E1%86%BA_2021-11-26_%E1%84%8B%E1%85%A9%E1%84%8C%E1%85%A5%E1%86%AB_11.38.52.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Credential=AKIAT73L2G45EIPT3X45%2F20220104%2Fus-west-2%2Fs3%2Faws4_request&X-Amz-Date=20220104T073242Z&X-Amz-Expires=86400&X-Amz-Signature=28c33681414caf84eb1042e60b88ae2ccccb5881bc35b4725f5d8f83ac42839e&X-Amz-SignedHeaders=host&response-content-disposition=filename%20%3D%22%25E1%2584%2589%25E1%2585%25B3%25E1%2584%258F%25E1%2585%25B3%25E1%2584%2585%25E1%2585%25B5%25E1%2586%25AB%25E1%2584%2589%25E1%2585%25A3%25E1%2586%25BA%25202021-11-26%2520%25E1%2584%258B%25E1%2585%25A9%25E1%2584%258C%25E1%2585%25A5%25E1%2586%25AB%252011.38.52.png%22&x-id=GetObject)

#### 준비

`Apche Kafka`를 사용할 준비가 되어 있다는 전제하에 아래 커맨드를 실행하여, 토픽을 생성한다.

```bash
bin/kafka-topics.sh --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 --topic push-request-json --create 
bin/kafka-topics.sh --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 --topic push-send-succeed-json --create 
bin/kafka-topics.sh --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 --topic push-send-retry-json --create 
bin/kafka-topics.sh --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 --topic push-send-failed-json --create 
```

각각의 토픽의 역할은 다음과 같다. (파티셔닝 동작을 확인하기 위해 파티션을 3으로 설정함)

- push-request-json: 발송요청 Topic
- push-send-succeed-json: 발송성공 Topic
- push-send-retry-json: 발송재시도 Topic
- push-send-failed-json: 발송실패 Topic

#### 실행 및 테스트

어플리케이션 실행

```bash
mvn clean compile spring-boot:run
```

푸시 발송 요청
```bash
curl -X POST 'http://localhost:8888/api/push/send' \
  -H 'Content-Type: application/json' \
  -d '{"uuid": "ABCDE","receiverId": "2","message": "hello"}'
```

#### 참고

- [달빛방랑 - Spring Cloud Stream with RabbitMQ](https://medium.com/@odysseymoon/spring-cloud-stream-with-rabbitmq-c273ed9a79b)