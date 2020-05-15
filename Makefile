PACKAGE = de.markusfisch.android.motoscore

all: debug install start

debug:
	./gradlew assembleDebug

release: lint
	@./gradlew assembleRelease \
		-Pandroid.injected.signing.store.file=$(ANDROID_KEYFILE) \
		-Pandroid.injected.signing.store.password=$(ANDROID_STORE_PASSWORD) \
		-Pandroid.injected.signing.key.alias=$(ANDROID_KEY_ALIAS) \
		-Pandroid.injected.signing.key.password=$(ANDROID_KEY_PASSWORD)

lint:
	./gradlew lintDebug

install:
	adb $(TARGET) install -r app/build/outputs/apk/debug/app-debug.apk

start:
	adb $(TARGET) shell 'am start -n \
		$(PACKAGE).debug/$(PACKAGE).activity.SplashActivity'

uninstall:
	adb $(TARGET) uninstall $(PACKAGE).debug

meminfo:
	adb shell dumpsys meminfo $(PACKAGE).debug

images:
	svg/update.sh

avocado:
	avocado $(shell fgrep -rl '<vector' app/src/main/res)

clean:
	./gradlew clean
