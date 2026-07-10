package com.intellipolis.ai.service;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.Optional;
@Service
public class UrbanAiService {
 private static final String SYSTEM="""
  당신은 IntelliPolis의 도시계획 의사결정 지원 에이전트입니다. 제공된 데이터만 사용하고 입력에 없는 수치나 예산을 만들지 마세요.
  실제 행정 결정처럼 단정하지 말고 도시계획 전문가 검토가 필요한 한국어 참고 제안으로 작성하세요.
  """;
 private final ChatClient client;
 public UrbanAiService(ObjectProvider<ChatModel> provider){ChatModel model=provider.getIfAvailable();this.client=model==null?null:ChatClient.create(model);}
 public Optional<String> explain(String json){
  if(client==null)return Optional.empty();
  try {String value=client.prompt().system(SYSTEM).user("다음 규칙 기반 계획을 간결하게 설명하세요: "+json).call().content();return Optional.ofNullable(value).filter(s->!s.isBlank());}
  catch(Exception ignored){return Optional.empty();}
 }
}
