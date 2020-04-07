package com.mrpowergamerbr.loritta.profile

import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.dao.GuildProfile
import com.mrpowergamerbr.loritta.dao.Profile
import com.mrpowergamerbr.loritta.network.Databases
import com.mrpowergamerbr.loritta.tables.GuildProfiles
import com.mrpowergamerbr.loritta.tables.Profiles
import com.mrpowergamerbr.loritta.tables.Reputations
import com.mrpowergamerbr.loritta.userdata.MongoServerConfig
import com.mrpowergamerbr.loritta.utils.*
import com.mrpowergamerbr.loritta.utils.locale.LegacyBaseLocale
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import javax.imageio.ImageIO

class DefaultProfileCreator : ProfileCreator {
	override fun create(sender: ProfileUserInfoData, user: ProfileUserInfoData, userProfile: Profile, guild: Guild?, serverConfig: MongoServerConfig?, badges: List<BufferedImage>, locale: LegacyBaseLocale, background: BufferedImage, aboutMe: String, member: Member?): BufferedImage {
		val profileWrapper = ImageIO.read(File(Loritta.ASSETS, "profile_wrapper_v4.png"))
		val profileWrapperOverlay = ImageIO.read(File(Loritta.ASSETS, "profile_wrapper_v4_overlay.png"))
		val base = BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB) // Base
		val graphics = base.graphics.enableFontAntiAliasing()

		val avatar = LorittaUtils.downloadImage(user.avatarUrl)!!.getScaledInstance(115, 115, BufferedImage.SCALE_SMOOTH)

		graphics.drawImage(background.getScaledInstance(800, 600, BufferedImage.SCALE_SMOOTH), 0, 0, null)

		graphics.drawImage(profileWrapper, 0, 0, null)
		graphics.drawImage(avatar.toBufferedImage().makeRoundedCorners(115), 6, 6, null)

		val whitneyMedium = FileInputStream(File(Loritta.ASSETS + "whitney-medium.ttf")).use {
			Font.createFont(Font.TRUETYPE_FONT, it)
		}
		val whitneySemiBold = FileInputStream(File(Loritta.ASSETS + "whitney-semibold.ttf")).use {
			Font.createFont(Font.TRUETYPE_FONT, it)
		}
		val whitneyBold = FileInputStream(File(Loritta.ASSETS + "whitney-bold.ttf")).use {
			Font.createFont(Font.TRUETYPE_FONT, it)
		}

		val whitneySemiBold38 = whitneySemiBold.deriveFont(38f)
		val whitneyMedium22 = whitneySemiBold.deriveFont(22f)
		val whitneyBold20 = whitneyBold.deriveFont(20f)
		val whitneySemiBold20 = whitneySemiBold.deriveFont(20f)

		fun drawSection(title: String, subtext: String, x: Int, y: Int): Pair<Int, Int> {
			graphics.font = whitneyBold20
			graphics.drawText(title, x, y, 800 - 6)
			graphics.font = whitneySemiBold20
			graphics.drawText(subtext, x, y + 19, 800 - 6)
			return Pair(x, y + 19)
		}

		graphics.font = whitneySemiBold38

		if (badges.isEmpty()) {
			graphics.drawText(user.name, 139, 71, 517 - 6)
		} else { // Caso exista badges, nós iremos alterar um pouquinho aonde o nome é desenhado
			graphics.drawText(user.name, 139, 61 - 4, 517 - 6)
			var x = 139
			var y = 70

			// E agora desenhar as badges
			badges.withIndex().forEach { (index, originalBadge) ->
				val badge = originalBadge.getScaledInstance(27, 27, BufferedImage.SCALE_SMOOTH)
				graphics.drawImage(badge, x, y, null)
				x += 27 + 8

				if (index % 10 == 9) {
					x = 139
					y += 27
				}
			}
		}

		val globalPosition = transaction(Databases.loritta) {
			Profiles.select { Profiles.xp greaterEq userProfile.xp }.count()
		}
		drawSection("Global", "#$globalPosition / ${userProfile.xp} XP", 562, 21)

		if (guild != null) {
			val guildIcon = LorittaUtils.downloadImage(guild.iconUrl?.replace("jpg", "png") ?: "https://emojipedia-us.s3.amazonaws.com/thumbs/320/google/56/shrug_1f937.png")!!.getScaledInstance(38, 38, BufferedImage.SCALE_SMOOTH)

			val localProfile = transaction(Databases.loritta) {
				GuildProfile.find { (GuildProfiles.guildId eq guild.idLong) and (GuildProfiles.userId eq user.id) }.firstOrNull()
			}

			val localPosition = if (localProfile != null) {
				transaction(Databases.loritta) {
					GuildProfiles.select { (GuildProfiles.guildId eq guild.idLong) and (GuildProfiles.xp greaterEq localProfile.xp) }.count()
				}
			} else {
				null
			}

			val xpLocal = localProfile?.xp

			graphics.font = whitneyBold20
			graphics.drawText(guild.name, 562, 61, 800 - 6)
			graphics.font = whitneySemiBold20
			if (xpLocal != null) {
				graphics.drawText("#$localPosition / $xpLocal XP", 562, 78, 800 - 6)
			} else {
				graphics.drawText("???", 562, 78, 800 - 6)
			}

			graphics.drawImage(guildIcon.toBufferedImage().makeRoundedCorners(38), 520, 44, null)
		}

		val reputations = transaction(Databases.loritta) {
			com.mrpowergamerbr.loritta.tables.Reputations.select { Reputations.receivedById eq user.id }.count()
		}

		drawSection("Reputação", "$reputations reps", 562, 102)

		val globalEconomyPosition = transaction(Databases.loritta) {
			Profiles.select { Profiles.money greaterEq userProfile.money }.count()
		}

		drawSection(locale["ECONOMY_NamePlural"], "#$globalEconomyPosition / ${userProfile.money}", 562, 492)

		val marriage = transaction(Databases.loritta) { userProfile.marriage }

		if (marriage != null) {
			val marriedWithId = if (marriage.user1 == user.id) {
				marriage.user2
			} else {
				marriage.user1
			}.toString()

			val marriedWith = runBlocking { lorittaShards.retrieveUserInfoById(marriedWithId.toLong()) }

			if (marriedWith != null)
				drawSection(locale.toNewLocale()["profile.marriedWith"], marriedWith.name + "#" + marriedWith.discriminator, 562, 535)
		}

		graphics.font = whitneyMedium22

		ImageUtils.drawTextWrapSpaces(aboutMe, 6, 493, 517 - 6, 600, graphics.fontMetrics, graphics)

		graphics.drawImage(profileWrapperOverlay, 0, 0, null)

		return base
	}
}