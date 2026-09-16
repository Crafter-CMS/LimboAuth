/*
 * Copyright (C) 2021 - 2025 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limboauth;

import net.elytrium.commons.config.YamlConfig;

public class Messages extends YamlConfig {

  @Ignore
  public static final Messages IMP = new Messages();

  @Comment("Don't use \\n, use {NL} for new line, and {PRFX} for prefix.")

  @Create
  public GENERAL GENERAL;

  public static class GENERAL {
    public String RELOAD = "{PRFX} &aYapılandırma ve dil dosyaları başarıyla yenilendi!";
    public String ERROR_OCCURRED = "{PRFX} &cBir iç hata meydana geldi!";
    public String RATELIMITED = "{PRFX} &cLütfen tekrar denemeden önce bekleyin!";
    public String DATABASE_ERROR_KICK = "{PRFX} &cBir veritabanı hatası meydana geldi!";
    public String NOT_PLAYER = "{PRFX} &cBu komutu sadece oyuncular kullanabilir!";
    public String NOT_REGISTERED = "{PRFX} &cKayıtlı değilsiniz veya hesabınız &6PREMIUM&c!";
    public String CRACKED_COMMAND = "{PRFX}{NL}&aHesabınız &6PREMIUM &aolduğu için bu komutu kullanamazsınız!";
    public String WRONG_PASSWORD = "{PRFX} &cŞifre hatalı!";
    public String EVENT_CANCELLED = "{PRFX} Giriş işlemi iptal edildi.";
  }

  @Create
  public AUTH AUTH;

  public static class AUTH {
    public String LOGIN = "{PRFX} &aLütfen &6/login <şifre> &akullanarak giriş yapınız, &6{0} &ahakkınız var.";
    public String LOGIN_WRONG_PASSWORD = "{PRFX} &cHatalı şifre girdiniz, kalan hakkınız: &6{0}&c.";
    public String LOGIN_WRONG_PASSWORD_KICK = "{PRFX}{NL}&cÇok fazla hatalı şifre girdiniz!";
    public String LOGIN_SUCCESSFUL = "{PRFX} &aBaşarıyla giriş yapıldı!";
    public String LOGIN_TITLE = "&fLütfen giriş yapın: &6/login <şifre>";
    public String LOGIN_SUBTITLE = "&aKalan hakkınız: &6{0}";
    public String LOGIN_SUCCESSFUL_TITLE = "{PRFX}";
    public String LOGIN_SUCCESSFUL_SUBTITLE = "&aBaşarıyla giriş yapıldı!";

    public String BOSSBAR = "{PRFX} Giriş yapmak için &6{0} &fsaniyeniz kaldı.";
    public String TIMES_UP = "{PRFX}{NL}&cGiriş süreniz doldu.";
    public String SESSION_EXPIRED = "{PRFX} Oturumunuzun süresi doldu, lütfen tekrar giriş yapınız.";

    public String LOGIN_PREMIUM = "{PRFX} Premium hesabınız ile otomatik olarak giriş yapıldı!";
    public String LOGIN_PREMIUM_TITLE = "{PRFX} Hoş Geldiniz!";
    public String LOGIN_PREMIUM_SUBTITLE = "&aPremium oyuncu olarak doğrulandınız!";

    public String LOGIN_FLOODGATE = "{PRFX} Bedrock hesabınız ile otomatik olarak giriş yapıldı!";
    public String LOGIN_FLOODGATE_TITLE = "{PRFX} Hoş Geldiniz!";
    public String LOGIN_FLOODGATE_SUBTITLE = "&aBedrock oyuncusu olarak doğrulandınız!";
  }

  @Create
  public REGISTER REGISTER;

  public static class REGISTER {
    public String REGISTER = "{PRFX} Lütfen kayıt olun: &6/register <şifre> <şifre-tekrar>";
    public String REGISTER_DIFFERENT_PASSWORDS = "{PRFX} &cGirilen şifreler birbiriyle uyuşmuyor!";
    public String REGISTER_PASSWORD_TOO_SHORT = "{PRFX} &cGirdiğiniz şifre çok kısa, daha uzun bir şifre seçin!";
    public String REGISTER_PASSWORD_TOO_LONG = "{PRFX} &cGirdiğiniz şifre çok uzun, daha kısa bir şifre seçin!";
    public String REGISTER_PASSWORD_UNSAFE = "{PRFX} &cGirdiğiniz şifre güvenli değil, lütfen farklı bir şifre belirleyin!";
    public String REGISTER_SUCCESSFUL = "{PRFX} &aBaşarıyla kayıt olundu!";
    public String REGISTER_TITLE = "{PRFX}";
    public String REGISTER_SUBTITLE = "&aLütfen kayıt olun: &6/register <şifre> <şifre-tekrar>";
    public String REGISTER_SUCCESSFUL_TITLE = "{PRFX}";
    public String REGISTER_SUCCESSFUL_SUBTITLE = "&aBaşarıyla kayıt olundu!";
  }

  @Create
  public CRAFTER CRAFTER;

  public static class CRAFTER {
    public String EMAIL_INPUT_PROMPT = "{PRFX} &eLütfen hesabınıza ait geçerli e-posta adresinizi giriniz: &6/email <adres>";
    public String REGISTER_EMAIL_SENT = "{PRFX} &aKayıt başarılı! E-posta adresinize (&e{0}&a) gönderilen kodu giriniz: &6/verify <kod>";
    public String SERVICE_UNAVAILABLE = "{PRFX} &cKayıt ve kimlik doğrulama servisine ulaşılamıyor. Lütfen daha sonra tekrar deneyiniz.";
    public String REGISTER_ERROR = "{PRFX} &cKayıt hatası: &e{0}";
    public String TWO_FACTOR_EMAIL_SENT = "{PRFX} &eİki adımlı doğrulama kodu e-postanıza gönderildi.";
    public String TWO_FACTOR_DISCORD_SENT = "{PRFX} &eİki adımlı doğrulama kodu Discord üzerinden iletildi.";
    public String TWO_FACTOR_CODE_PROMPT = "{PRFX} &eLütfen kodu giriniz: &6/2fa <kod>";
    public String TWO_FACTOR_RESEND_HINT = "{PRFX} &7(Tekrar göndermek için: &6/2fa resend&7)";
    public String TWO_FACTOR_APP_REQUIRED = "{PRFX} &eİki adımlı doğrulama gereklidir.";
    public String TWO_FACTOR_APP_PROMPT = "{PRFX} &eAuthenticator uygulamanızdaki kodu veya kurtarma kodunuzu giriniz: &6/2fa <kod>";
    public String LOGIN_EMAIL_INPUT_REQUIRED = "{PRFX} &eOyundan kayıt olduğunuz için lütfen geçerli e-posta adresinizi giriniz: &6/email <adres>";
    public String LOGIN_EMAIL_SENT = "{PRFX} &eHesabınıza giriş yapabilmek için (&e{0}&e) e-posta adresinize gönderilen kodu giriniz: &6/verify <kod>";
    public String LOGIN_EMAIL_RESEND_HINT = "{PRFX} &7(Tekrar göndermek için: &6/email resend&7)";
    public String RESEND_SUCCESS = "{PRFX} &aYeni doğrulama kodu başarıyla gönderildi.";
    public String RESEND_FAILED = "{PRFX} &cKod gönderilemedi: &e{0}";
    public String RESEND_ERROR = "{PRFX} &cKod gönderme hatası: &e{0}";
    public String TWO_FACTOR_SUCCESS = "{PRFX} &aİki adımlı doğrulama başarılı!";
    public String TWO_FACTOR_INVALID_CODE = "{PRFX} &cGeçersiz veya süresi dolmuş 2FA kodu!";
    public String TWO_FACTOR_ERROR = "{PRFX} &c2FA doğrulama hatası: &e{0}";
    public String EMAIL_UPDATED_CODE_SENT = "{PRFX} &aDoğrulama kodu &6{0} &aadresine gönderildi!";
    public String EMAIL_CODE_INPUT_PROMPT = "{PRFX} &eLütfen 6 haneli kodu giriniz: &6/verify <kod> &eya da &6/email <kod>";
    public String EMAIL_UPDATE_FAILED = "{PRFX} &cE-posta güncellenemedi! Geçerli ve kullanılmayan bir e-posta adresi giriniz.";
    public String EMAIL_UPDATE_ERROR = "{PRFX} &cE-posta güncelleme hatası: &e{0}";
    public String EMAIL_VERIFY_SUCCESS = "{PRFX} &aE-posta adresiniz başarıyla doğrulandı!";
    public String EMAIL_VERIFY_INVALID_CODE = "{PRFX} &cGeçersiz veya süresi dolmuş doğrulama kodu!";
    public String EMAIL_VERIFY_ERROR = "{PRFX} &cE-posta doğrulama hatası: &e{0}";
    public String AUTH_ERROR = "{PRFX} &cKimlik doğrulama hatası: &e{0}";

    public String REMINDER_2FA = "§eLütfen 2FA kodunuzu giriniz: §6/2fa <kod>";
    public String REMINDER_EMAIL_INPUT = "§eLütfen geçerli e-posta adresinizi giriniz: §6/email <adres>";
    public String REMINDER_EMAIL_CODE = "§eLütfen e-posta doğrulama kodunuzu giriniz: §6/verify <kod>";
  }

  @Create
  public KICK KICK;

  public static class KICK {
    public String NICKNAME_INVALID = "{PRFX}{NL}&cKullanıcı adınız geçersiz karakterler içeriyor. Lütfen kullanıcı adınızı değiştiriniz!";
    public String RECONNECT = "{PRFX}{NL}&cHesabınızı doğrulamak için lütfen sunucuya yeniden bağlanın!";
    public String IP_LIMIT = "{PRFX}{NL}{NL}&cBu IP adresinden açılabilecek maksimum hesap sınırına ulaştınız.";
    public String WRONG_NICKNAME_CASE = "{PRFX}{NL}&cLütfen büyük/küçük harfe dikkat ederek &6{0} &ckullanıcı adıyla girin, &6{1} &cdeğil.";
    public String REGISTRATIONS_DISABLED = "{PRFX} Kayıtlar şu anda devre dışıdır.";
  }

  @Create
  public TOTP TOTP;

  public static class TOTP {
    public String PROMPT = "{PRFX} Lütfen 2FA anahtarınızı giriniz: &6/2fa <anahtar>";
    public String TITLE = "{PRFX}";
    public String SUBTITLE = "&a2FA anahtarınızı giriniz: &6/2fa <anahtar>";
    public String SUCCESSFUL = "{PRFX} &a2FA başarıyla etkinleştirildi!";
    public String DISABLED = "{PRFX} &a2FA başarıyla devre dışı bırakıldı!";
    public String USAGE = "{PRFX} Kullanım: &6/2fa enable <mevcut şifre>&f veya &6/2fa disable <2fa kodu>&f.";
    public String WRONG = "{PRFX} &cHatalı 2FA kodu!";
    public String ALREADY_ENABLED = "{PRFX} &c2FA zaten etkin. Devre dışı bırakmak için: &6/2fa disable <kod>&c.";
    public String QR = "{PRFX} Tarayıcıda 2FA QR kodunu açmak için buraya tıklayın.";
    public String TOKEN = "{PRFX} &a2FA anahtarınız &7(Kopyalamak için tıkla)&a: &6{0}";
    public String RECOVERY = "{PRFX} &aKurtarma kodlarınız &7(Kopyalamak için tıkla)&a: &6{0}";
  }

  @Create
  public COMMANDS COMMANDS;

  public static class COMMANDS {
    public String CHANGE_PASSWORD_SUCCESSFUL = "{PRFX} &aŞifreniz başarıyla değiştirildi!";
    public String CHANGE_PASSWORD_USAGE = "{PRFX} Kullanım: &6/changepassword <eski şifre> <yeni şifre>";
    public String UNREGISTER_SUCCESSFUL = "{PRFX}{NL}&aKaydınız başarıyla silindi!";
    public String UNREGISTER_USAGE = "{PRFX} Kullanım: &6/unregister <mevcut şifre> confirm";
    public String PREMIUM_SUCCESSFUL = "{PRFX}{NL}&aHesap durumunuz başarıyla &6PREMIUM &aolarak güncellendi!";
    public String ALREADY_PREMIUM = "{PRFX} &cHesabınız zaten &6PREMIUM&c!";
    public String NOT_PREMIUM = "{PRFX} &cHesabınız &6PREMIUM &cdeğil!";
    public String PREMIUM_USAGE = "{PRFX} Kullanım: &6/premium <mevcut şifre> confirm";
    public String DESTROY_SESSION_SUCCESSFUL = "{PRFX} &eOturumunuz sonlandırıldı, tekrar bağlandığınızda giriş yapmanız gerekecektir.";
  }

  @Create
  public ADMIN ADMIN;

  public static class ADMIN {
    public String FORCE_UNREGISTER_SUCCESSFUL = "{PRFX} &6{0} &aadlı oyuncunun kaydı başarıyla silindi!";
    public String FORCE_UNREGISTER_KICK = "{PRFX}{NL}&aKaydınız bir yönetici tarafından silindi!";
    public String FORCE_UNREGISTER_NOT_SUCCESSFUL = "{PRFX} &c{0} adlı oyuncunun kaydı silinemedi.";
    public String FORCE_UNREGISTER_USAGE = "{PRFX} Kullanım: &6/forceunregister <kullanıcı_adı>";
    public String FORCE_CHANGE_PASSWORD_SUCCESSFUL = "{PRFX} &a{0} adlı oyuncunun şifresi başarıyla değiştirildi!";
    public String FORCE_CHANGE_PASSWORD_MESSAGE = "{PRFX} &aŞifreniz bir yönetici tarafından &6{0} &aolarak değiştirildi!";
    public String FORCE_CHANGE_PASSWORD_NOT_SUCCESSFUL = "{PRFX} &c{0} adlı oyuncunun şifresi değiştirilemedi.";
    public String FORCE_CHANGE_PASSWORD_NOT_REGISTERED = "{PRFX} &c{0} adlı oyuncu kayıtlı değil.";
    public String FORCE_CHANGE_PASSWORD_USAGE = "{PRFX} Kullanım: &6/forcechangepassword <kullanıcı_adı> <yeni_şifre>";
    public String FORCE_REGISTER_USAGE = "{PRFX} Kullanım: &6/forceregister <kullanıcı_adı> <şifre>";
    public String FORCE_REGISTER_INCORRECT_NICKNAME = "{PRFX} &cKullanıcı adı geçersiz karakterler içeriyor.";
    public String FORCE_REGISTER_TAKEN_NICKNAME = "{PRFX} &cBu kullanıcı adı zaten alınmış.";
    public String FORCE_REGISTER_SUCCESSFUL = "{PRFX} &a{0} adlı oyuncu başarıyla kaydedildi!";
    public String FORCE_REGISTER_NOT_SUCCESSFUL = "{PRFX} &c{0} adlı oyuncu kaydedilemedi.";
    public String FORCE_LOGIN_USAGE = "{PRFX} Kullanım: &6/forcelogin <kullanıcı_adı>";
    public String FORCE_LOGIN_SUCCESSFUL = "{PRFX} &a{0} adlı oyuncu başarıyla giriş yaptırıldı!";
    public String FORCE_LOGIN_UNKNOWN_PLAYER = "{PRFX} &c{0} kullanıcı adlı aktif oyuncu bulunamadı!";

    public String HELP_HEADER = "Bu sunucu LimboAuth ve LimboAPI kullanmaktadır.";
    public String HELP_COPYRIGHT = "(C) 2021 - 2025 Elytrium & Crafter";
    public String HELP_URL = "https://crafter.net.tr";
    public String SUBCOMMANDS_AVAILABLE = "Kullanılabilir alt komutlar:";
    public String SUBCOMMANDS_NONE = "Kullanabileceğiniz bir alt komut bulunmuyor.";
    public String SUBCOMMAND_RELOAD_DESC = "Yapılandırma ve dil dosyalarını yeniler.";
    public String SUBCOMMAND_TEST_CRAFTER_DESC = "Crafter CMS API bağlantısını test eder.";
    public String TEST_CRAFTER_TESTING = "{PRFX} &eCrafter CMS API bağlantısı test ediliyor...";
    public String TEST_CRAFTER_RESULT = "{PRFX} &aTest sonucu: &f{0}";
    public String TEST_CRAFTER_NOT_AVAILABLE = "{PRFX} &cCrafter CMS API istemcisi aktif değil.";
  }
}
