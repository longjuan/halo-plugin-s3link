import {definePlugin} from "@halo-dev/console-shared";
import HomeView from "./views/HomeView.vue";
import {markRaw} from "vue";
import CarbonFolderDetailsReference from "~icons/carbon/folder-details-reference";
// TODO 隐藏菜单至插件管理页面 https://github.com/halo-dev/halo/pull/4041
export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/s3link",
        children: [
          {
            path: "",
            name: "S3Link",
            component: HomeView,
            meta: {
              title: "关联S3文件",
              searchable: true,
              menu: {
                name: "关联S3文件",
                group: "工具",
                icon: markRaw(CarbonFolderDetailsReference),
                priority: 0,
              },
            },
          },
        ],
      },
    },
  ],
  extensionPoints: {},
});
