package io.embodit.trafficsignals.domain

/**
 * Lesson and judging copy (design-package gestures.list, judging, content.levels). The Chinese step text is the
 * Ministry of Public Security original (《交通警察手势信号》, 2007), word for word; the English is a plain translation
 * shown with the original in small print (design S15).
 */
data class Tr(val zh: String, val en: String) {
    fun of(zhLang: Boolean) = if (zhLang) zh else en
}

object Content {
    val gestureName =
        mapOf(
            Gesture.STOP to Tr("停止信号", "Stop"),
            Gesture.STRAIGHT to Tr("直行信号", "Go straight"),
            Gesture.LEFT to Tr("左转弯信号", "Turn left"),
            Gesture.LEFT_WAIT to Tr("左转弯待转信号", "Left turn: wait in box"),
            Gesture.RIGHT to Tr("右转弯信号", "Turn right"),
            Gesture.LANE_CHANGE to Tr("变道信号", "Change lane"),
            Gesture.SLOW to Tr("减速慢行信号", "Slow down"),
            Gesture.PULL_OVER to Tr("示意车辆靠边停车信号", "Pull over"),
        )

    /** What the gesture means (original wording). */
    val meaning =
        mapOf(
            Gesture.STOP to Tr("不准前方车辆通行", "Traffic in front of him must not go"),
            Gesture.STRAIGHT to Tr("准许右方直行的车辆通行", "Traffic on his right may go straight"),
            Gesture.LEFT to Tr("准许车辆左转弯，在不妨碍被放行车辆通行的情况下可以掉头", "Traffic may turn left (U-turn if it doesn't block released traffic)"),
            Gesture.LEFT_WAIT to Tr("准许左方左转弯的车辆进入路口，沿左转弯行驶方向靠近路口中心，等候左转弯信号", "Left-turners on his left may pull in towards the centre and wait for the left-turn signal"),
            Gesture.RIGHT to Tr("准许右方的车辆右转弯", "Traffic on his right may turn right"),
            Gesture.LANE_CHANGE to Tr("车辆腾空指定的车道，减速慢行", "Clear the indicated lane and slow down"),
            Gesture.SLOW to Tr("车辆减速慢行", "Slow down"),
            Gesture.PULL_OVER to Tr("车辆靠边停车", "Pull over and stop"),
        )

    val mnemonic =
        mapOf(
            Gesture.STOP to Tr("左手高高举起，掌心冲前：前面的车停", "Left hand high, palm out: traffic in front stops"),
            Gesture.STRAIGHT to Tr("两臂平伸，右手往胸前招两下：右边的直行", "Both arms out, right hand waves in twice: right side, go straight"),
            Gesture.LEFT to Tr("右手前推挡住前面，左手往右前摆两下：左边的左转", "Right palm pushes forward, left arm sweeps across twice: left side, turn left"),
            Gesture.LEFT_WAIT to Tr("左手斜下往下压两下：左边左转的先进来等着", "Left arm low, pressing down twice: left-turners on his left, pull in and wait"),
            Gesture.RIGHT to Tr("左手前推挡住前面，右手往左前摆两下：右边的右转", "Left palm pushes forward, right arm sweeps across twice: right side, turn right"),
            Gesture.LANE_CHANGE to Tr("右手前伸掌心朝左，横着往左扫两下：让出这条道", "Right arm forward, palm left, sweeping left twice: clear this lane"),
            Gesture.SLOW to Tr("右手斜前方平伸、掌心向下往下压两下：慢点", "Right arm out front-right, palm down, pressing twice: slow down"),
            Gesture.PULL_OVER to Tr("左手高举 + 右手斜下横扫两下：靠边停", "Left hand high plus right arm sweeping low twice: pull over"),
        )

    /** Step text, original Chinese and English (43 steps). */
    val steps: Map<Gesture, List<Tr>> =
        mapOf(
            Gesture.STOP to listOf(
                Tr("左臂由前向上直伸与身体成135度，掌心向前与身体平行，五指并拢，面部及目光平视前方", "Left arm raised straight forward and up to 135° from the body, palm forward parallel to the body, fingers together, eyes straight ahead"),
                Tr("左臂垂直放下，恢复立正姿势", "Left arm drops straight down, back to attention"),
            ),
            Gesture.STRAIGHT to listOf(
                Tr("左臂向左平伸与身体成90度，掌心向前，五指并拢，面部及目光同时转向左方45度", "Left arm out to the left at 90°, palm forward, fingers together; face and eyes turn 45° left"),
                Tr("右臂向右平伸与身体成90度，掌心向前，五指并拢，面部及目光同时转向右方45度", "Right arm out to the right at 90°, palm forward, fingers together; face and eyes turn 45° right"),
                Tr("右臂水平向左摆动与身体成90度，小臂弯屈至与大臂成90度，掌心向内与左胸衣兜相对，小臂与前胸平行，面部及目光同时转向左方45度", "Right arm swings level to the left to 90°, forearm bent 90° to the upper arm, palm in towards the left breast pocket, forearm parallel to the chest; face and eyes turn 45° left"),
                Tr("右大臂不动，右小臂水平向右摆动与身体成90度，掌心向左，五指并拢", "Right upper arm still, right forearm swings level to the right to 90°, palm left, fingers together"),
                Tr("右小臂弯屈至与大臂成90度，掌心向内与左胸衣兜相对，与前胸平行，完成第二次摆动", "Right forearm bends back to 90°, palm in towards the left breast pocket, parallel to the chest: second wave done"),
                Tr("收右臂", "Right arm down"),
                Tr("收左臂，面部及目光转向前方，恢复立正姿势", "Left arm down, face and eyes forward, back to attention"),
            ),
            Gesture.LEFT to listOf(
                Tr("右臂向前平伸与身体成90度，掌心向前，手掌与手臂夹角不低于60度，五指并拢，面部及目光同时转向左方45度", "Right arm straight forward at 90°, palm forward, hand bent up at least 60° to the arm, fingers together; face and eyes turn 45° left"),
                Tr("左臂与手掌平直向右前方摆动，手臂与身体成45度，掌心向右，中指尖至上衣中缝，高度至上衣最下面一个纽扣", "Left arm and hand swing straight to the front-right at 45° from the body, palm right, middle fingertip at the shirt's centre seam, as low as the bottom button"),
                Tr("左臂回位至不超过裤缝，面部及目光保持目视左方45度，完成第一次摆动", "Left arm back, not past the trouser seam, eyes still 45° left: first swing done"),
                Tr("重复（2）动作", "Repeat step 2"),
                Tr("重复（3）动作，完成第二次摆动", "Repeat step 3: second swing done"),
                Tr("收右臂，面部及目光转向前方，恢复立正姿势", "Right arm down, face and eyes forward, back to attention"),
            ),
            Gesture.LEFT_WAIT to listOf(
                Tr("左臂向左平伸与身体成45度，掌心向下，五指并拢，面部及目光同时转向左方45度", "Left arm out to the left at 45° from the body, palm down, fingers together; face and eyes turn 45° left"),
                Tr("左臂与手掌平直向下方摆动，手臂与身体成15度，面部及目光保持目视左方45度，完成第一次摆动", "Left arm and hand swing straight down to 15° from the body, eyes still 45° left: first swing done"),
                Tr("重复（1）动作", "Repeat step 1"),
                Tr("重复（2）动作，完成第二次摆动", "Repeat step 2: second swing done"),
                Tr("收左臂，面部及目光转向前方，恢复立正姿势", "Left arm down, face and eyes forward, back to attention"),
            ),
            Gesture.RIGHT to listOf(
                Tr("左臂向前平伸与身体成90度，掌心向前，手掌与手臂夹角不低于60度，五指并拢，面部及目光同时转向右方45度", "Left arm straight forward at 90°, palm forward, hand bent up at least 60°, fingers together; face and eyes turn 45° right"),
                Tr("右臂与手掌平直向左前方摆动，手臂与身体成45度，掌心向左，中指尖至上衣中缝，高度至上衣最下面一个纽扣", "Right arm and hand swing straight to the front-left at 45°, palm left, middle fingertip at the centre seam, as low as the bottom button"),
                Tr("右臂回位至不超过裤缝，面部及目光保持目视右方45度，完成第一次摆动", "Right arm back, not past the trouser seam, eyes still 45° right: first swing done"),
                Tr("重复（2）动作", "Repeat step 2"),
                Tr("重复（3）动作，完成第二次摆动", "Repeat step 3: second swing done"),
                Tr("收左臂，面部及目光转向前方，恢复立正姿势", "Left arm down, face and eyes forward, back to attention"),
            ),
            Gesture.LANE_CHANGE to listOf(
                Tr("面向来车方向，右臂向前平伸与身体成90度，掌心向左，五指并拢，面部及目光平视前方", "Facing the oncoming traffic, right arm straight forward at 90°, palm left, fingers together, eyes ahead"),
                Tr("右臂向左水平摆动与身体成45度，完成第一次摆动", "Right arm swings level to the left to 45°: first swing done"),
                Tr("恢复至（1）动作", "Back to step 1"),
                Tr("重复（2）动作，完成第二次摆动", "Repeat step 2: second swing done"),
                Tr("收右臂，回复立正姿势", "Right arm down, back to attention"),
            ),
            Gesture.SLOW to listOf(
                Tr("右臂向右前方平伸，与肩平行，与身体成135度，掌心向下，五指并拢，面部及目光同时转向右方45度", "Right arm out to the front-right at shoulder height, 135° from the body, palm down, fingers together; face and eyes turn 45° right"),
                Tr("右臂与手掌平直向下方摆动，手臂与身体成45度，面部及目光保持目视右方45度，完成第一次摆动", "Right arm and hand swing straight down to 45° from the body, eyes still 45° right: first swing done"),
                Tr("重复（1）动作", "Repeat step 1"),
                Tr("重复（2）动作，完成第二次摆动", "Repeat step 2: second swing done"),
                Tr("收右臂，面部及目光转向前方，恢复立正姿势", "Right arm down, face and eyes forward, back to attention"),
            ),
            Gesture.PULL_OVER to listOf(
                Tr("面向来车方向，右臂前伸与身体成45度，掌心向左，五指并拢，面部及目光平视前方", "Facing the oncoming traffic, right arm forward and down at 45°, palm left, fingers together, eyes ahead"),
                Tr("左臂由前向上伸直与身体成135度，掌心向前与身体平行，五指并拢", "Left arm raised straight forward and up to 135°, palm forward parallel to the body, fingers together"),
                Tr("右臂向左水平摆动与身体成45度，完成第一次摆动", "Right arm swings level to the left by 45°: first swing done"),
                Tr("右臂恢复至（1）动作", "Right arm back to step 1"),
                Tr("重复（3）动作，完成第二次摆动", "Repeat step 3: second swing done"),
                Tr("右臂恢复至（1）动作", "Right arm back to step 1"),
                Tr("双臂同时放下，恢复立正姿势", "Both arms down together, back to attention"),
            ),
        )

    val answer =
        mapOf(
            Answer.GO to Tr("走", "Go"),
            Answer.STOP to Tr("停", "Stop"),
            Answer.WAIT to Tr("进待转区", "Pull into waiting box"),
            Answer.SLOW to Tr("减速慢行", "Slow down"),
            Answer.LANE to Tr("腾空车道", "Clear the lane"),
            Answer.PULL to Tr("靠边停车", "Pull over"),
        )

    val side =
        mapOf(
            Side.FRONT to Tr("前方", "in front of him"),
            Side.LEFT to Tr("左方", "on his left"),
            Side.BACK to Tr("后方", "behind him"),
            Side.RIGHT to Tr("右方", "on his right"),
        )

    val facing =
        mapOf(
            Facing.SOUTH to Tr("面向你", "facing you"),
            Facing.WEST to Tr("面向你的左边", "facing your left"),
            Facing.NORTH to Tr("背对你", "with his back to you"),
            Facing.EAST to Tr("面向你的右边", "facing your right"),
        )

    val lane =
        mapOf(
            Lane.LEFT to Tr("左转车道", "left-turn lane"),
            Lane.STRAIGHT to Tr("直行车道", "straight-on lane"),
            Lane.RIGHT to Tr("右转车道", "right-turn lane"),
        )

    val levelName =
        listOf(
            Tr("正面四令", "Face to face"),
            Tr("他的右边是你", "On his right"),
            Tr("他的左边是你", "On his left"),
            Tr("四面八方", "Any direction"),
            Tr("换道再看", "Same signal, other lane"),
            Tr("限时判官", "Timed judge"),
        )

    val levelGoal =
        listOf(
            Tr("他面向你，先把四个「冲你来的」手势认熟。", "He faces you: get the four signals meant for you down first."),
            Tr("你在他右手边。直行和右转放的都是他右边的车。", "You're on his right. Straight-on and right-turn both release his right side."),
            Tr("你在他左手边。分清「左转」和「左转待转」。", "You're on his left. Tell 'turn left' from 'wait in the box'."),
            Tr("他随便朝哪边站。先判方位，再认手势。", "He faces any way. Work out where you are first, then read the signal."),
            Tr("同一个手势，换条车道答案就可能变。", "Same signal, different lane: the answer can change."),
            Tr("手势打完 5 秒内答，练条件反射。", "Answer within 5 s after the signal ends. Reflex time."),
        )

    /** Line ② of the verdict: who this gesture releases. */
    fun releaseLine(g: Gesture): Tr =
        when (g) {
            Gesture.STOP -> Tr("停止信号：他前方的车不准走", "Stop: traffic in front of him may not go")
            Gesture.STRAIGHT -> Tr("直行信号：只放他右方的直行车", "Go straight: only his right side, straight on")
            Gesture.LEFT -> Tr("左转弯信号：放他左方的左转车", "Turn left: his left side may turn left")
            Gesture.LEFT_WAIT -> Tr("左转待转：他左方的左转车进待转区等着", "Wait in box: left-turners on his left pull in and wait")
            Gesture.RIGHT -> Tr("右转弯信号：放他右方的右转车", "Turn right: his right side may turn right")
            Gesture.LANE_CHANGE -> Tr("变道信号：面前来车腾空车道、减速", "Change lane: oncoming traffic clears the lane and slows")
            Gesture.SLOW -> Tr("减速慢行：面前来车慢下来", "Slow down: oncoming traffic slows")
            Gesture.PULL_OVER -> Tr("靠边停车：面前来车靠边停", "Pull over: oncoming traffic pulls over")
        }

    /** Lesson 0 (orientation): four questions "where are you relative to him". */
    val lesson0Title = Tr("先认方位", "Where am I?")
    val lesson0Steps =
        listOf(
            Tr("他面向你：你在他的前方。他的左手在你的右边。", "He faces you: you're in front of him. His left hand is on your right."),
            Tr("他面向你的左边：他的左手边就是你，你在他的左方。", "He faces your left: you're on his left-hand side."),
            Tr("他背对你：你在他的后方。看帽子后脑勺就知道。", "His back to you: you're behind him. Look for the back of his cap."),
            Tr("他面向你的右边：你在他的右方。", "He faces your right: you're on his right-hand side."),
        )
    val lesson0Facing = listOf(Facing.SOUTH, Facing.WEST, Facing.NORTH, Facing.EAST)
}
