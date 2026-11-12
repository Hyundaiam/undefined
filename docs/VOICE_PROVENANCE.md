# 임시 음성 출처

`app/src/main/assets/voices/legacy`의 MP3 44개는 기존 곁 웹 프로토타입에서 사용하던 임시 파일입니다. 기존 파일을 유지한 채 구현하고 추후 직접 녹음한 파일로 교체하라는 사용자 결정에 따라 가져왔습니다.

- 생성 엔진: Qwen3-TTS
- 모델 식별자: `mlx-community/Qwen3-TTS-12Hz-1.7B-VoiceDesign-bf16`, `mlx-community/Qwen3-TTS-12Hz-1.7B-Base-bf16`
- 기존 manifest에 파일별 SHA-256과 크기를 보존했습니다.
- 사람 녹음으로 소개하지 않습니다. 브라우저/Android 시스템 TTS 호출은 없습니다.
- 모델 가중치·API 키·사용자의 음성 녹음 원본은 이 저장소에 포함하지 않았습니다.
- 기존 음성의 대사는 `legacy_voice_texts.json`과 앱의 미리 듣기 화면에서 확인할 수 있습니다.

직접 녹음한 파일을 제공받기 전에는 `voices/recorded/manifest.json`이 비어 있습니다. 이후에는 개인 파일이 기존 임시 음성보다 먼저 재생됩니다. 공개 배포에 적용할 음성·모델 관련 권리와 SDK 라이선스는 해당 공급자의 조건을 따라야 하며 이 문서는 별도 권리 부여를 뜻하지 않습니다.
