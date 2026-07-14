# Keep the independent feed-list entry points and models used by reflection/binding.
-keep class com.chat.feedlist.** { *; }
# Profile route is opened by class name from the independent feed-list module.
-keep class com.chat.partner.profile.PartnerProfileRoute { public *; }
